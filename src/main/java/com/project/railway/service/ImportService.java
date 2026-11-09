package com.project.railway.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.project.railway.dto.Coach;
import com.project.railway.dto.Route;
import com.project.railway.dto.Schedule;
import com.project.railway.dto.Station;
import com.project.railway.dto.StationMaster;
import com.project.railway.dto.Train;
import com.project.railway.helper.CoachLayout;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.Quota;
import com.project.railway.helper.QuotaPlanner;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.helper.Seat_type;
import com.project.railway.helper.TrainImport;
import com.project.railway.repository.StationMaster_Repository;
import com.project.railway.repository.Train_Repository;

import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;

/**
 * Bulk import of trains for the admin: the bundled Indian Railways list, or a CSV / JSON file.
 *
 * Runs in the background (one import at a time) and reports progress through status():
 *  1. New station codes are added to the station directory (existing stations are kept as they are).
 *  2. Each train gets its running days, stops (km filled in from station positions when missing),
 *     base fare, coaches and every seat split into quotas, exactly like a train set up by hand.
 *     Trains whose number already exists are skipped, never overwritten.
 * Trains are saved 20 at a time; if one of them fails, that group is retried one train at a time so
 * a single bad train never loses the others.
 */
@Service
public class ImportService {

	private static final Logger log = LoggerFactory.getLogger(ImportService.class);
	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
	private static final String BUNDLED = "data/indian-railways.json.gz";
	private static final int CHUNK = 20;
	private static final int SEATS_PER_INSERT = 500;
	private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

	/** Coaches per class when a train doesn't say (the admin can change these for each import) */
	public static final Map<Seat_type, Integer> DEFAULT_COACHES = defaultCoaches();

	/** Class order of coach numbering, as when a train is set up by hand */
	private static final List<Seat_type> COACH_ORDER = List.of(Seat_type.AC1_TIER, Seat_type.EXECUTIVE_CHAIR_CAR,
			Seat_type.AC2_TIER, Seat_type.AC3_TIER, Seat_type.AC3_ECONOMY, Seat_type.AC_CHAIR_CAR, Seat_type.SLEEPER_CLASS,
			Seat_type.SECOND_CLASS);

	@Autowired
	private StationMaster_Repository masterRepository;
	@Autowired
	private Train_Repository trainRepository;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private TransactionTemplate tx;
	@Autowired
	private JwtUtil jwtUtil;
	@Autowired
	private AuditService audit;
	@Autowired(required = false)
	private LoggingSystem loggingSystem;

	/** SQL loggers turned down while importing (hundreds of thousands of statements) */
	private static final List<String> SQL_LOGGERS = List.of("org.hibernate.SQL", "org.hibernate.orm.jdbc.bind");

	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "train-import");
		t.setDaemon(true);
		return t;
	});

	/** What the admin chose for this import */
	public record Options(Map<String, Integer> coaches, Double baseFare, Boolean addCoaches, String onlyStations,
			Integer limit) {
	}

	/** Progress, shown on the admin's import page */
	public record Status(String state, String source, int total, int done, int imported, int skipped, int failed,
			int stationsAdded, long seats, List<String> problems, String startedAt, String finishedAt, String message) {
	}

	private volatile Status status = new Status("IDLE", null, 0, 0, 0, 0, 0, 0, 0, List.of(), null, null,
			"No import has run since the server started.");

	@PreDestroy
	void stop() {
		worker.shutdownNow();
	}

	/* ------------------------------------------------------------------ endpoints */

	public ResponseEntity<ResponseStructure<Status>> status(String token) {
		if (!isAdmin(token)) {
			return reply(HttpStatus.UNAUTHORIZED, "Invalid or Expired Token, Please Login Again", null);
		}
		return reply(HttpStatus.OK, status.message(), status);
	}

	/** What the bundled list holds, before importing it */
	public ResponseEntity<ResponseStructure<Map<String, Object>>> bundledSummary(String token) {
		ResponseStructure<Map<String, Object>> structure = new ResponseStructure<>();
		if (!isAdmin(token)) {
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			structure.setMessage("Invalid or Expired Token, Please Login Again");
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		}
		try (InputStream in = new ClassPathResource(BUNDLED).getInputStream()) {
			TrainImport.Batch batch = TrainImport.readBundled(in);
			Set<Integer> existing = new HashSet<>(trainRepository.findAllNumbers());
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("source", batch.source());
			out.put("trains", batch.trains().size());
			out.put("stations", batch.stations().size());
			out.put("alreadyInDatabase", batch.trains().stream().filter(t -> existing.contains(t.number())).count());
			Map<String, Long> byType = batch.trains().stream()
					.collect(Collectors.groupingBy(TrainImport.ImportTrain::type, LinkedHashMap::new, Collectors.counting()));
			out.put("byType", byType);
			Map<String, Integer> defaults = new LinkedHashMap<>();
			DEFAULT_COACHES.forEach((k, v) -> defaults.put(CoachLayout.shortCode(k), v));
			out.put("defaultCoaches", defaults);
			structure.setData2(out);
			structure.setStatus(HttpStatus.OK.value());
			structure.setMessage("Bundled train list");
			return new ResponseEntity<>(structure, HttpStatus.OK);
		} catch (IOException e) {
			structure.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
			structure.setMessage("The bundled train list is missing from this build.");
			return new ResponseEntity<>(structure, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public ResponseEntity<ResponseStructure<Status>> startBundled(String token, Options options) {
		if (!isAdmin(token)) {
			return reply(HttpStatus.UNAUTHORIZED, "Invalid or Expired Token, Please Login Again", null);
		}
		TrainImport.Batch batch;
		try (InputStream in = new ClassPathResource(BUNDLED).getInputStream()) {
			batch = TrainImport.readBundled(in);
		} catch (IOException e) {
			return reply(HttpStatus.INTERNAL_SERVER_ERROR, "The bundled train list is missing from this build.", status);
		}
		return start(batch, options, adminName(token));
	}

	public ResponseEntity<ResponseStructure<Status>> startUpload(String token, MultipartFile file, Options options) {
		if (!isAdmin(token)) {
			return reply(HttpStatus.UNAUTHORIZED, "Invalid or Expired Token, Please Login Again", null);
		}
		if (file == null || file.isEmpty()) {
			return reply(HttpStatus.BAD_REQUEST, "Choose a CSV or JSON file.", status);
		}
		if (file.getSize() > MAX_UPLOAD_BYTES) {
			return reply(HttpStatus.BAD_REQUEST, "The file is larger than 20 MB. Split it into smaller files.", status);
		}
		String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
		try {
			String text = new String(file.getBytes(), StandardCharsets.UTF_8);
			boolean json = name.endsWith(".json") || text.stripLeading().startsWith("{");
			TrainImport.Batch batch = json ? TrainImport.readJson(text) : TrainImport.readCsv(text);
			if (batch.trains().isEmpty()) {
				Status s = new Status("FAILED", batch.source(), 0, 0, 0, 0, 0, 0, 0, batch.problems(), now(), now(),
						"No train in the file could be imported.");
				status = s;
				return reply(HttpStatus.BAD_REQUEST, s.message(), s);
			}
			return start(batch, options, adminName(token));
		} catch (IllegalArgumentException e) {
			return reply(HttpStatus.BAD_REQUEST, e.getMessage(), status);
		} catch (IOException e) {
			return reply(HttpStatus.BAD_REQUEST, "The file couldn't be read.", status);
		}
	}

	/* ------------------------------------------------------------------ the import */

	private synchronized ResponseEntity<ResponseStructure<Status>> start(TrainImport.Batch batch, Options options,
			String actor) {
		if ("RUNNING".equals(status.state())) {
			return reply(HttpStatus.CONFLICT, "An import is already running. Wait for it to finish.", status);
		}
		Map<Seat_type, Integer> coachDefaults;
		try {
			coachDefaults = coachDefaults(options);
		} catch (IllegalArgumentException e) {
			return reply(HttpStatus.BAD_REQUEST, e.getMessage(), status);
		}
		double baseFare = options == null || options.baseFare() == null ? 0 : options.baseFare();
		if (baseFare < 0 || baseFare > 100000) {
			return reply(HttpStatus.BAD_REQUEST, "Enter a base fare between 0 and 100000.", status);
		}
		boolean addCoaches = options == null || options.addCoaches() == null || options.addCoaches();

		// Filters: only trains stopping at some stations, at most N trains
		List<TrainImport.ImportTrain> trains = batch.trains();
		Set<String> only = new HashSet<>();
		if (options != null && options.onlyStations() != null) {
			for (String c : options.onlyStations().split("[\\s,;]+")) {
				String code = TrainImport.code(c);
				if (code != null) {
					only.add(code);
				}
			}
		}
		if (!only.isEmpty()) {
			trains = trains.stream().filter(t -> t.stops().stream().anyMatch(s -> only.contains(s.code()))).toList();
		}
		if (options != null && options.limit() != null && options.limit() > 0 && options.limit() < trains.size()) {
			trains = trains.subList(0, options.limit());
		}
		if (trains.isEmpty()) {
			return reply(HttpStatus.BAD_REQUEST, "No train matches those stations.", status);
		}

		List<TrainImport.ImportTrain> todo = trains;
		Status s = new Status("RUNNING", batch.source(), todo.size(), 0, 0, 0, 0, 0, 0, new ArrayList<>(batch.problems()),
				now(), null, "Importing " + todo.size() + " trains…");
		status = s;
		audit.recordAs(actor, "TRAINS_IMPORT_STARTED", batch.source(), todo.size() + " trains, coaches "
				+ (addCoaches ? coachDefaults : "none") + ", base fare " + baseFare);
		worker.submit(() -> run(batch, todo, coachDefaults, baseFare, addCoaches));
		return reply(HttpStatus.ACCEPTED, s.message(), s);
	}

	private void run(TrainImport.Batch batch, List<TrainImport.ImportTrain> trains, Map<Seat_type, Integer> coachDefaults,
			double baseFare, boolean addCoaches) {
		Progress p = new Progress(trains.size(), status.problems());
		Map<String, LogLevel> sqlLogging = quietSqlLogs();
		try {
			// 1. Station directory
			Map<String, StationMaster> directory = new HashMap<>();
			masterRepository.findAll().forEach(m -> directory.put(m.getCode(), m));
			Map<String, TrainImport.ImportStation> given = new HashMap<>();
			batch.stations().forEach(st -> given.put(st.code(), st));
			List<StationMaster> added = new ArrayList<>();
			List<StationMaster> positioned = new ArrayList<>();
			for (TrainImport.ImportTrain t : trains) {
				for (TrainImport.ImportStop stop : t.stops()) {
					StationMaster m = directory.get(stop.code());
					TrainImport.ImportStation st = given.get(stop.code());
					if (m == null) {
						String name = st != null ? st.name() : stop.name();
						if (name == null) {
							continue; // reported per train below
						}
						m = st != null
								? new StationMaster(st.code(), st.name(), blankOr(st.city(), TrainImport.cityOf(st.name())), st.state(),
										st.latitude(), st.longitude())
								: new StationMaster(stop.code(), stop.name(), TrainImport.cityOf(stop.name()), null);
						directory.put(m.getCode(), m);
						added.add(m);
					} else if (!m.hasPosition() && st != null && st.latitude() != null && st.longitude() != null
							&& !added.contains(m)) {
						m.setLatitude(st.latitude());
						m.setLongitude(st.longitude());
						positioned.add(m);
					}
				}
			}
			for (int i = 0; i < added.size(); i += 500) {
				List<StationMaster> part = added.subList(i, Math.min(added.size(), i + 500));
				tx.executeWithoutResult(x -> masterRepository.saveAll(part));
			}
			if (!positioned.isEmpty()) {
				tx.executeWithoutResult(x -> masterRepository.saveAll(positioned));
			}
			p.stationsAdded = added.size();
			publish(batch, p, null);

			Map<String, double[]> positions = new HashMap<>();
			directory.values().forEach(m -> {
				if (m.hasPosition()) {
					positions.put(m.getCode(), new double[] { m.getLatitude(), m.getLongitude() });
				}
			});

			// 2. Trains, 20 at a time
			Set<Integer> existing = new HashSet<>(trainRepository.findAllNumbers());
			for (int i = 0; i < trains.size(); i += CHUNK) {
				if (Thread.currentThread().isInterrupted()) {
					throw new IllegalStateException("The server is shutting down.");
				}
				List<TrainImport.ImportTrain> group = new ArrayList<>();
				for (TrainImport.ImportTrain t : trains.subList(i, Math.min(trains.size(), i + CHUNK))) {
					String unknown = t.stops().stream().map(TrainImport.ImportStop::code).filter(c -> !directory.containsKey(c))
							.findFirst().orElse(null);
					if (existing.contains(t.number())) {
						p.skipped++;
						p.done++;
					} else if (unknown != null) {
						p.problem("Train " + t.number() + ": station " + unknown
								+ " isn't in the station directory and the file gives no name for it.");
						p.failed++;
						p.done++;
					} else {
						group.add(t);
					}
				}
				try {
					long seats = tx.execute(x -> saveAll(group, directory, positions, coachDefaults, baseFare, addCoaches));
					p.seats += seats;
					p.imported += group.size();
					p.done += group.size();
					group.forEach(t -> existing.add(t.number()));
				} catch (RuntimeException e) {
					for (TrainImport.ImportTrain t : group) { // find the one that fails
						try {
							long seats = tx.execute(x -> saveAll(List.of(t), directory, positions, coachDefaults, baseFare, addCoaches));
							p.seats += seats;
							p.imported++;
							existing.add(t.number());
						} catch (RuntimeException one) {
							p.failed++;
							p.problem("Train " + t.number() + ": " + rootMessage(one));
							log.warn("Import of train {} failed: {}", t.number(), one.toString());
						}
						p.done++;
					}
				}
				publish(batch, p, null);
			}
			String done = p.imported + " trains imported" + (p.skipped > 0 ? ", " + p.skipped + " already there (skipped)" : "")
					+ (p.failed > 0 ? ", " + p.failed + " couldn't be imported" : "") + ". " + p.stationsAdded
					+ " stations added to the directory.";
			publishDone(batch, p, "DONE", done);
			audit.recordAs("system", "TRAINS_IMPORTED", batch.source(), done);
			log.info("Train import finished: {}", done);
		} catch (RuntimeException e) {
			log.error("Train import stopped", e);
			publishDone(batch, p, "FAILED", "The import stopped: " + rootMessage(e) + " Trains saved so far are kept.");
		} finally {
			restoreSqlLogs(sqlLogging);
		}
	}

	private Map<String, LogLevel> quietSqlLogs() {
		Map<String, LogLevel> before = new HashMap<>();
		if (loggingSystem == null) {
			return before;
		}
		for (String name : SQL_LOGGERS) {
			LoggerConfiguration c = loggingSystem.getLoggerConfiguration(name);
			before.put(name, c == null ? null : c.getConfiguredLevel());
			loggingSystem.setLogLevel(name, LogLevel.WARN);
		}
		log.info("SQL logging is paused while trains are imported");
		return before;
	}

	private void restoreSqlLogs(Map<String, LogLevel> before) {
		if (loggingSystem != null) {
			before.forEach(loggingSystem::setLogLevel);
		}
	}

	/** Saves trains in the current transaction; returns the number of seats created */
	private long saveAll(List<TrainImport.ImportTrain> group, Map<String, StationMaster> directory,
			Map<String, double[]> positions, Map<Seat_type, Integer> coachDefaults, double baseFare, boolean addCoaches) {
		long seats = 0;
		List<Coach> allCoaches = new ArrayList<>();
		for (TrainImport.ImportTrain t : group) {
			Train train = new Train();
			train.setTrainNumber(t.number());
			train.setTrainName(t.name());
			train.setTrain_type(t.type());

			Schedule schedule = new Schedule();
			schedule.setRunningWeeks(t.days().toArray(new String[0]));
			schedule.setTrain(train);
			train.setSchedule(schedule);

			Route route = new Route();
			route.setPrice(baseFare);
			route.setTrain(train);
			train.setRoute(route);

			List<Double> km = TrainImport.fillKm(t.stops(), positions);
			List<Station> stops = new ArrayList<>();
			double sumKm = 0;
			for (int i = 0; i < t.stops().size(); i++) {
				TrainImport.ImportStop in = t.stops().get(i);
				Station stop = new Station();
				stop.setStationCode(in.code());
				stop.setStationName(directory.get(in.code()).getName());
				stop.setArrivalTime(in.arrival());
				stop.setDepartureTime(in.departure());
				// stops keep the km from the PREVIOUS stop (the app adds them up for a journey); files give km from the start
				double segment = i == 0 ? 0 : km.get(i) - km.get(i - 1);
				stop.setKm(segment);
				stop.setDay(in.day());
				stop.setPrice(BigDecimal.valueOf(baseFare + 1.11 * segment).setScale(2, RoundingMode.HALF_UP).doubleValue());
				stop.setRoute(route);
				stop.setTrains(train);
				sumKm += segment;
				stops.add(stop);
			}
			train.setStations(stops);
			train.setSourceStation(stops.get(0).getStationName());
			train.setDestinationStation(stops.get(stops.size() - 1).getStationName());
			route.setStartStation(train.getSourceStation());
			route.setEndStation(train.getDestinationStation());
			route.setTotal_distance(sumKm);

			List<Coach> coaches = new ArrayList<>();
			if (addCoaches) {
				Map<Seat_type, Integer> counts = coachCounts(t, coachDefaults);
				for (Seat_type type : COACH_ORDER) {
					for (int n = 1; n <= counts.getOrDefault(type, 0); n++) {
						Coach c = new Coach();
						c.setTrain(train);
						c.setCoachType(type.name());
						c.setCoachNumber(CoachLayout.prefix(type) + n);
						c.setNumberOfSeats(CoachLayout.capacity(type));
						coaches.add(c);
					}
				}
			}
			train.setCoaches(coaches);
			entityManager.persist(train);
			allCoaches.addAll(coaches);
		}
		entityManager.flush(); // coach ids exist now

		// Seats: every berth of every coach, split into quotas with the default shares, many rows per INSERT
		List<Object[]> rows = new ArrayList<>();
		Map<String, List<Coach>> byTrainAndClass = new LinkedHashMap<>();
		for (Coach c : allCoaches) {
			byTrainAndClass.computeIfAbsent(c.getTrain().getTrainNumber() + "|" + c.getCoachType(), k -> new ArrayList<>())
					.add(c);
		}
		for (List<Coach> classCoaches : byTrainAndClass.values()) {
			Seat_type type = Seat_type.valueOf(classCoaches.get(0).getCoachType());
			List<QuotaPlanner.SeatRef> refs = new ArrayList<>();
			List<Object[]> classRows = new ArrayList<>();
			for (Coach c : classCoaches) {
				for (int no = 1; no <= c.getNumberOfSeats(); no++) {
					String berth = CoachLayout.berthType(type, no);
					refs.add(new QuotaPlanner.SeatRef(classRows.size(), c.getId(), no, berth));
					classRows.add(new Object[] { c.getId(), no, berth, null });
				}
			}
			Map<Long, Quota> plan = QuotaPlanner.plan(refs, QuotaPlanner.Shares.defaults(type));
			for (int i = 0; i < classRows.size(); i++) {
				classRows.get(i)[3] = plan.getOrDefault((long) i, Quota.GN).name();
			}
			rows.addAll(classRows);
		}
		for (int i = 0; i < rows.size(); i += SEATS_PER_INSERT) {
			List<Object[]> part = rows.subList(i, Math.min(rows.size(), i + SEATS_PER_INSERT));
			String sql = "INSERT INTO coach_seat (coach_id, seat_number, berth_type, quota) VALUES "
					+ String.join(",", java.util.Collections.nCopies(part.size(), "(?,?,?,?)"));
			Object[] args = new Object[part.size() * 4];
			for (int j = 0; j < part.size(); j++) {
				System.arraycopy(part.get(j), 0, args, j * 4, 4);
			}
			jdbc.update(sql, args);
			seats += part.size();
		}
		entityManager.clear();
		return seats;
	}

	/** Coach counts for one train: its own numbers, or the defaults for each class it has */
	static Map<Seat_type, Integer> coachCounts(TrainImport.ImportTrain t, Map<Seat_type, Integer> defaults) {
		if (t.coaches() != null) {
			return t.coaches();
		}
		Map<Seat_type, Integer> out = new EnumMap<>(Seat_type.class);
		List<Seat_type> classes = t.classes() == null || t.classes().isEmpty()
				? List.of(Seat_type.SLEEPER_CLASS, Seat_type.AC3_TIER, Seat_type.AC2_TIER)
				: t.classes();
		int total = 0;
		for (Seat_type c : classes) {
			int n = Math.min(defaults.getOrDefault(c, 0), CoachLayout.MAX_COACHES - total);
			if (n > 0) {
				out.merge(c, n, Integer::sum);
				total += n;
			}
		}
		return out;
	}

	private static Map<Seat_type, Integer> coachDefaults(Options options) {
		Map<Seat_type, Integer> out = new EnumMap<>(DEFAULT_COACHES);
		if (options != null && options.coaches() != null) {
			int total = 0;
			for (Map.Entry<String, Integer> e : options.coaches().entrySet()) {
				int n = e.getValue() == null ? 0 : e.getValue();
				if (n < 0 || n > CoachLayout.MAX_COACHES) {
					throw new IllegalArgumentException("Coaches per class must be 0 to " + CoachLayout.MAX_COACHES + ".");
				}
				Seat_type type = TrainImport.seatType(e.getKey());
				out.put(type, n);
			}
			for (int n : out.values()) {
				total += n;
			}
			if (total == 0) {
				throw new IllegalArgumentException("Choose at least one coach, or untick \"Add coaches\".");
			}
		}
		return out;
	}

	private static Map<Seat_type, Integer> defaultCoaches() {
		Map<Seat_type, Integer> m = new EnumMap<>(Seat_type.class);
		m.put(Seat_type.AC1_TIER, 1);
		m.put(Seat_type.AC2_TIER, 1);
		m.put(Seat_type.AC3_TIER, 2);
		m.put(Seat_type.AC3_ECONOMY, 0);
		m.put(Seat_type.AC_CHAIR_CAR, 3);
		m.put(Seat_type.EXECUTIVE_CHAIR_CAR, 1);
		m.put(Seat_type.SLEEPER_CLASS, 4);
		m.put(Seat_type.SECOND_CLASS, 0);
		return java.util.Collections.unmodifiableMap(m);
	}

	/* ------------------------------------------------------------------ progress */

	private static final class Progress {
		final int total;
		int done, imported, skipped, failed, stationsAdded;
		long seats;
		final List<String> problems;

		Progress(int total, List<String> problems) {
			this.total = total;
			this.problems = new ArrayList<>(problems);
		}

		void problem(String p) {
			if (problems.size() < TrainImport.MAX_PROBLEMS) {
				problems.add(p);
			}
		}
	}

	private void publish(TrainImport.Batch batch, Progress p, String message) {
		Status old = status;
		status = new Status("RUNNING", batch.source(), p.total, p.done, p.imported, p.skipped, p.failed, p.stationsAdded,
				p.seats, List.copyOf(p.problems), old.startedAt(), null,
				message != null ? message : "Imported " + p.done + " of " + p.total + " trains…");
	}

	private void publishDone(TrainImport.Batch batch, Progress p, String state, String message) {
		Status old = status;
		status = new Status(state, batch.source(), p.total, p.done, p.imported, p.skipped, p.failed, p.stationsAdded,
				p.seats, List.copyOf(p.problems), old.startedAt(), now(), message);
	}

	/* ------------------------------------------------------------------ helpers */

	private String adminName(String token) {
		String[] who = jwtUtil.getSubject(token);
		return who == null ? "unknown" : who[0];
	}

	private boolean isAdmin(String token) {
		String[] who = jwtUtil.getSubject(token);
		return who != null && "admin".equals(who[1]);
	}

	private static String now() {
		return LocalDateTime.now(IST).format(WHEN);
	}

	private static String blankOr(String s, String fallback) {
		return s == null || s.isBlank() ? fallback : s;
	}

	private static String rootMessage(Throwable e) {
		Throwable t = e;
		while (t.getCause() != null && t.getCause() != t) {
			t = t.getCause();
		}
		String m = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
		return m.length() > 200 ? m.substring(0, 200) + "…" : m;
	}

	private static <T> ResponseEntity<ResponseStructure<T>> reply(HttpStatus code, String message, T data) {
		ResponseStructure<T> structure = new ResponseStructure<>();
		structure.setStatus(code.value());
		structure.setMessage(message);
		structure.setData2(data);
		return new ResponseEntity<>(structure, code);
	}
}
