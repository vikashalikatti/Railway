package com.project.railway.service.implementation;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.project.railway.dto.Admin;
import com.project.railway.dto.Booking;
import com.project.railway.dto.Coach;
import com.project.railway.dto.Route;
import com.project.railway.dto.Schedule;
import com.project.railway.dto.CoachSeat;
import com.project.railway.dto.Station;
import com.project.railway.dto.StationMaster;
import com.project.railway.dto.Train;
import com.project.railway.helper.CoachLayout;
import com.project.railway.helper.EmailService;
import com.project.railway.helper.AccountRules;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.LoginAttemptService;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.helper.Seat_type;
import com.project.railway.repository.Admin_Repository;
import com.project.railway.repository.Coach_Repository;
import com.project.railway.repository.Route_Repository;
import com.project.railway.repository.Schedule_Repository;
import com.project.railway.repository.Book_Repository;
import com.project.railway.repository.CoachSeat_Repository;
import com.project.railway.repository.StationMaster_Repository;
import com.project.railway.repository.Station_Repository;
import com.project.railway.repository.Train_Repository;
import com.project.railway.service.Admin_Service;
import com.project.railway.service.StationDirectory;

import freemarker.core.ParseException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateNotFoundException;

@Service
public class Admin_Service_Implementation implements Admin_Service {

	@Autowired
	Admin_Repository admin_Repository;

	@Autowired
	BCryptPasswordEncoder encoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	JwtUtil jwtUtil;

	@Autowired
	EmailService emailService;

	@Autowired
	Train_Repository train_Repository;

	@Autowired
	Schedule_Repository schedule_Repository;

	@Autowired
	Station_Repository station_Repository;

	@Autowired
	Route_Repository route_Repository;

	@Autowired
	CoachSeat_Repository coachSeat_Repository;

	@Autowired
	Book_Repository booking_Repository;

	@Autowired
	StationMaster_Repository stationMaster_Repository;

	@Autowired
	StationDirectory stationDirectory;

	@Autowired
	private LoginAttemptService loginAttempts;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	Coach_Repository coach_Repository;

	@Override
	public ResponseEntity<ResponseStructure<Admin>> create(Admin admin) {
		ResponseStructure<Admin> structure = new ResponseStructure<>();
		// Only one admin, and only while none exists: afterwards nobody can create another
		if (admin_Repository.countByUsernameAndPassword(null, null) > 0) {
			return reply(structure, HttpStatus.ALREADY_REPORTED, "Admin Cannot More than one");
		}
		String problem = admin == null ? "Enter the admin details" : AccountRules.adminName(admin.getName());
		if (problem == null) {
			problem = AccountRules.newPassword(admin.getPassword());
		}
		if (problem == null && admin.getEmail() != null && !admin.getEmail().isBlank()) {
			problem = AccountRules.email(admin.getEmail());
		}
		if (problem != null) {
			return reply(structure, HttpStatus.BAD_REQUEST, problem);
		}
		Admin account = new Admin(); // only the chosen fields; id, role and token version are set here
		account.setName(admin.getName().trim());
		account.setPassword(encoder.encode(admin.getPassword()));
		account.setEmail(admin.getEmail() == null || admin.getEmail().isBlank() ? null : admin.getEmail().trim());
		account.setRole("admin");
		account.setTokenVersion(0);
		admin_Repository.save(account);
		structure.setData2(account); // the password is never written out
		return reply(structure, HttpStatus.CREATED, "Account Create for Admin");
	}

	@Override
	public ResponseEntity<ResponseStructure<Admin>> login(String name, String password)
			throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException {
		ResponseStructure<Admin> structure = new ResponseStructure<>();
		String account = "admin:" + (name == null ? "" : name.trim());
		long wait = loginAttempts.secondsLocked(account);
		if (wait > 0) {
			return reply(structure, HttpStatus.TOO_MANY_REQUESTS, LoginAttemptService.waitMessage(wait));
		}
		Admin admin = name == null ? null : admin_Repository.findByName(name.trim());
		boolean ok = admin != null && password != null && encoder.matches(password, admin.getPassword());
		if (!ok) {
			loginAttempts.failed(account);
			return reply(structure, HttpStatus.BAD_REQUEST, "Wrong admin name or password");
		}
		loginAttempts.succeeded(account);
		String token = jwtUtil.generateToken_for_admin(admin, new Date(System.currentTimeMillis() + JwtUtil.LOGIN_TTL_MS));
		structure.setData(token);
		String location = InetAddress.getLocalHost().getHostAddress();
		 emailService.sendInfoEmail(admin, location);
		return reply(structure, HttpStatus.OK, "Login Success");
	}

	@Override
	public ResponseEntity<ResponseStructure<Admin>> logout(String token, boolean everywhere) {
		ResponseStructure<Admin> structure = new ResponseStructure<>();
		String[] subject = jwtUtil.getSubject(token);
		if (subject != null && everywhere && "admin".equals(subject[1])) {
			Admin admin = admin_Repository.findByName(subject[0]);
			if (admin != null) {
				admin.setTokenVersion(JwtUtil.version(admin.getTokenVersion()) + 1);
				admin_Repository.save(admin);
			}
		}
		jwtUtil.logout(token);
		return reply(structure, HttpStatus.OK, everywhere ? "Logged out on every device" : "Logged out");
	}

	@Override
	public ResponseEntity<ResponseStructure<Train>> trainadd(Train train, String token) {
		ResponseStructure<Train> structure = new ResponseStructure<>();
		if (!isAdmin(token)) {
			structure.setMessage("Token Expired, Please Login Again");
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		} else {
			if (train.getTrainNumber() <= 0) {
				structure.setMessage("Enter a train number");
				structure.setStatus(HttpStatus.BAD_REQUEST.value());
				return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
			}
			if (train_Repository.existsById(train.getTrainNumber())) {
				structure.setMessage("Train " + train.getTrainNumber() + " already exists");
				structure.setStatus(HttpStatus.CONFLICT.value());
				return new ResponseEntity<>(structure, HttpStatus.CONFLICT);
			}
			train_Repository.save(train);
			structure.setData2(train);
			structure.setMessage("Train added");
			structure.setStatus(HttpStatus.OK.value());
			return new ResponseEntity<>(structure, HttpStatus.OK);
		}

	}

	@Override
	@Transactional
	public ResponseEntity<ResponseStructure<Schedule>> addSchedule(Schedule schedule, String token, int trainNo) {
		ResponseStructure<Schedule> structure = new ResponseStructure<>();
		if (!isAdmin(token)) {
			return reply(structure, HttpStatus.UNAUTHORIZED, "Token Expired, Please Login Again");
		}
		Train train = train_Repository.findByTrainNumber(trainNo);
		if (train == null) {
			return reply(structure, HttpStatus.NOT_FOUND, "Train not found");
		}
		String[] days = schedule == null ? null : schedule.getRunningWeeks();
		Set<String> picked = new LinkedHashSet<>();
		for (String d : days == null ? new String[0] : days) {
			String day = d == null ? "" : d.trim().toLowerCase(Locale.ROOT);
			try {
				DayOfWeek.valueOf(day.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return reply(structure, HttpStatus.BAD_REQUEST, "Unknown day " + d);
			}
			picked.add(day);
		}
		if (picked.isEmpty()) {
			return reply(structure, HttpStatus.BAD_REQUEST, "Pick at least one running day");
		}

		// A day can't be dropped while someone holds a ticket for that weekday
		Set<LocalDate> stranded = new TreeSet<>();
		for (Booking b : upcomingBookings(trainNo)) {
			if (!picked.contains(b.getJourneyDate().getDayOfWeek().name().toLowerCase(Locale.ROOT))) {
				stranded.add(b.getJourneyDate());
			}
		}
		if (!stranded.isEmpty()) {
			return reply(structure, HttpStatus.CONFLICT, "Passengers are booked on " + dates(stranded)
					+ ", so the train must keep running on those days");
		}

		Schedule saved = schedule_Repository.findByTrainTrainNumber(trainNo);
		boolean updating = saved != null;
		if (saved == null) {
			saved = new Schedule();
			saved.setTrain(train);
		}
		saved.setRunningWeeks(picked.toArray(new String[0]));
		saved = schedule_Repository.save(saved);
		train.setSchedule(saved);
		train_Repository.save(train);
		structure.setData2(saved);
		return reply(structure, HttpStatus.OK, updating ? "Running days updated" : "Schedule added successfully");
	}

	/**
	 * Sets the train's stops, in route order. Saving again replaces them: stops can be added,
	 * removed or reordered, and timings and km changed. While the train has upcoming bookings
	 * only timings and km can change, because tickets store their stops by position.
	 */
	@Override
	@Transactional
	public ResponseEntity<ResponseStructure<Train>> addStation(List<Station> stations, String token, int trainNo) {
		ResponseStructure<Train> structure = new ResponseStructure<>();
		if (!isAdmin(token)) {
			return reply(structure, HttpStatus.UNAUTHORIZED, "Token Expired, Please Login Again");
		}
		Train train = train_Repository.findByTrainNumber(trainNo);
		if (train == null) {
			return reply(structure, HttpStatus.NOT_FOUND, "Train not found");
		}
		if (stations == null || stations.size() < 2) {
			return reply(structure, HttpStatus.BAD_REQUEST, "A route needs at least 2 stops");
		}

		Set<String> seen = new HashSet<>();
		for (int i = 0; i < stations.size(); i++) {
			Station stop = stations.get(i);
			if (stop == null || stop.getStationName() == null || stop.getStationName().isBlank()) {
				return reply(structure, HttpStatus.BAD_REQUEST, "Stop " + (i + 1) + " has no station");
			}
			if (stationDirectory.unknownCode(stop.getStationCode())) {
				return reply(structure, HttpStatus.BAD_REQUEST, "Unknown station code " + stop.getStationCode());
			}
			stationDirectory.linkCode(stop); // sets the code and the directory's spelling of the name
			String key = stop.getStationCode() != null ? stop.getStationCode().toUpperCase(Locale.ROOT)
					: stop.getStationName().trim().toLowerCase(Locale.ROOT);
			if (!seen.add(key)) {
				return reply(structure, HttpStatus.BAD_REQUEST, stop.getStationName() + " is on the route twice");
			}
			if (stop.getArrivalTime() == null || stop.getDepartureTime() == null) {
				return reply(structure, HttpStatus.BAD_REQUEST, "Enter the times for " + stop.getStationName());
			}
			if (i > 0 && !(stop.getKm() > 0)) {
				return reply(structure, HttpStatus.BAD_REQUEST, "Enter the km to " + stop.getStationName());
			}
		}

		List<Station> current = train.getStations();
		if (current == null) {
			current = new ArrayList<>();
			train.setStations(current);
		}
		boolean sameStops = current.size() == stations.size();
		for (int i = 0; sameStops && i < current.size(); i++) {
			sameStops = sameStation(current.get(i), stations.get(i));
		}
		if (!sameStops && !upcomingBookings(trainNo).isEmpty()) {
			return reply(structure, HttpStatus.CONFLICT, "This train has upcoming bookings, so stops can't be added, "
					+ "removed or reordered. Timings and distances can still be changed.");
		}

		// Reuse the existing rows position by position (stops are ordered by id), add rows for
		// extra stops and delete rows left over at the end
		for (int i = 0; i < stations.size(); i++) {
			Station in = stations.get(i);
			Station row = i < current.size() ? current.get(i) : new Station();
			row.setStationName(in.getStationName().trim());
			row.setStationCode(in.getStationCode());
			row.setArrivalTime(in.getArrivalTime());
			row.setDepartureTime(in.getDepartureTime());
			row.setKm(i == 0 ? 0 : in.getKm());
			row.setTrains(train);
			if (i >= current.size()) {
				current.add(row);
			}
		}
		while (current.size() > stations.size()) {
			Station extra = current.remove(current.size() - 1);
			extra.setTrains(null); // so removing the stop can't cascade to the train
			extra.setRoute(null);
		}

		train.setSourceStation(current.get(0).getStationName());
		train.setDestinationStation(current.get(current.size() - 1).getStationName());
		if (train.getRoute() != null) {
			applyRoute(train.getRoute(), train);
		}
		train_Repository.save(train);
		structure.setData2(train);
		return reply(structure, HttpStatus.OK, "Stops saved");
	}

	/**
	 * Sets the base fare. Saving again changes it; tickets already booked keep the fare they paid.
	 */
	@Override
	@Transactional
	public ResponseEntity<ResponseStructure<Train>> addRoutesWithPrices(Route routes, String token, int trainNo) {
		ResponseStructure<Train> structure = new ResponseStructure<>();
		if (!isAdmin(token)) {
			return reply(structure, HttpStatus.UNAUTHORIZED, "Invalid or Expired Token, Please Login Again");
		}
		Train train = train_Repository.findByTrainNumber(trainNo);
		if (train == null) {
			return reply(structure, HttpStatus.NOT_FOUND, "Train not found");
		}
		if (train.getStations() == null || train.getStations().size() < 2) {
			return reply(structure, HttpStatus.BAD_REQUEST, "Add the stops before the fare");
		}
		if (routes == null || routes.getPrice() < 0 || routes.getPrice() > 100000) {
			return reply(structure, HttpStatus.BAD_REQUEST, "Enter a base fare between 0 and 100000");
		}

		Route route = train.getRoute();
		boolean updating = route != null;
		if (route == null) {
			route = new Route();
			route.setTrain(train);
		}
		route.setPrice(routes.getPrice());
		route = route_Repository.save(route);
		applyRoute(route, train);
		train.setRoute(route);
		train_Repository.save(train);
		structure.setData2(train);
		return reply(structure, HttpStatus.OK, updating ? "Fare updated" : "Route with Prices Added to Every Station");
	}

	/** Points every stop at the route, works out each stop's price and the route's total km */
	private void applyRoute(Route route, Train train) {
		List<Station> stops = train.getStations();
		double kms = 0;
		for (Station station : stops) {
			station.setRoute(route);
			station.setPrice(calculateStationPrice(route.getPrice(), station.getKm()));
			kms += station.getKm();
		}
		route.setStartStation(stops.get(0).getStationName());
		route.setEndStation(stops.get(stops.size() - 1).getStationName());
		route.setTotal_distance(kms);
	}

	private List<Booking> upcomingBookings(int trainNo) {
		return booking_Repository.findByTrainNumberAndJourneyDateGreaterThanEqualAndStatusNot(trainNo,
				LocalDate.now(), Booking.CANCELLED);
	}

	/** Same station: same code when both have one, otherwise the same name */
	private static boolean sameStation(Station a, Station b) {
		if (a.getStationCode() != null && b.getStationCode() != null) {
			return a.getStationCode().equalsIgnoreCase(b.getStationCode());
		}
		return a.getStationName() != null && b.getStationName() != null
				&& a.getStationName().trim().equalsIgnoreCase(b.getStationName().trim());
	}

	private static String dates(Set<LocalDate> days) {
		DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM yyyy");
		List<String> out = new ArrayList<>();
		for (LocalDate d : days) {
			if (out.size() == 3) {
				out.add("more");
				break;
			}
			out.add(d.format(f));
		}
		return String.join(", ", out);
	}

	private static <T> ResponseEntity<ResponseStructure<T>> reply(ResponseStructure<T> structure, HttpStatus status,
			String message) {
		structure.setMessage(message);
		structure.setStatus(status.value());
		return new ResponseEntity<>(structure, status);
	}

	private double calculateStationPrice(double routePrice, double stationDistance) {
		double pricePerKm = 1.11;
		BigDecimal stationPrice = new BigDecimal(routePrice + (pricePerKm * stationDistance));
		stationPrice = stationPrice.setScale(2, RoundingMode.HALF_UP);
		return stationPrice.doubleValue();
	}

	private double calculatePrice(double distance) {
		double ratePerKilometer = 0.48;
		return distance * ratePerKilometer;
	}

	/**
	 * Sets the train's coaches, e.g. {"SLEEPER_CLASS": 10, "AC3_TIER": 4, "AC2_TIER": 2}.
	 * Creates every seat with its number and berth type (CoachLayout). Replaces any
	 * existing coaches, but only while the train has no bookings.
	 */
	@Override
	@Transactional
	public ResponseEntity<ResponseStructure<Train>> addCoaches(Map<String, Integer> composition, String token,
			int trainNo) {
		ResponseStructure<Train> structure = new ResponseStructure<>();
		if (!isAdmin(token)) {
			structure.setMessage("Invalid or Expired Token, Please Login Again");
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		}
		Train train = train_Repository.findByTrainNumber(trainNo);
		if (train == null) {
			structure.setMessage("Train not found");
			structure.setStatus(HttpStatus.NOT_FOUND.value());
			return new ResponseEntity<>(structure, HttpStatus.NOT_FOUND);
		}
		if (composition == null || composition.isEmpty()) {
			structure.setMessage("Choose at least one coach");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		}

		// Validate: known classes, 0+ coaches each, 1 to 24 coaches in total
		Map<Seat_type, Integer> counts = new LinkedHashMap<>();
		int total = 0;
		for (Map.Entry<String, Integer> e : composition.entrySet()) {
			Seat_type type;
			try {
				type = Seat_type.valueOf(e.getKey().trim().toUpperCase());
			} catch (IllegalArgumentException ex) {
				structure.setMessage("Unknown class " + e.getKey());
				structure.setStatus(HttpStatus.BAD_REQUEST.value());
				return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
			}
			int n = e.getValue() == null ? 0 : e.getValue();
			if (n < 0) {
				structure.setMessage("Number of " + type + " coaches can't be negative");
				structure.setStatus(HttpStatus.BAD_REQUEST.value());
				return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
			}
			if (n > 0) {
				counts.merge(type, n, Integer::sum);
				total += n;
			}
		}
		if (total < 1 || total > CoachLayout.MAX_COACHES) {
			structure.setMessage("A train needs 1 to " + CoachLayout.MAX_COACHES + " coaches");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		}
		if (booking_Repository.existsByTrainNumber(trainNo)) {
			structure.setMessage("This train already has bookings, so its coaches can't be changed");
			structure.setStatus(HttpStatus.CONFLICT.value());
			return new ResponseEntity<>(structure, HttpStatus.CONFLICT);
		}

		// Remove old coaches and their seats
		List<Coach> oldCoaches = train.getCoaches() == null ? new ArrayList<>() : train.getCoaches();
		if (!oldCoaches.isEmpty()) {
			coachSeat_Repository.deleteByCoachIds(oldCoaches.stream().map(Coach::getId).toList());
			oldCoaches.clear(); // orphanRemoval deletes the coach rows
			train_Repository.saveAndFlush(train);
		}

		// Create coaches (S1, S2, B1, ...) in a fixed class order, then every seat in them
		List<Coach> newCoaches = new ArrayList<>();
		for (Seat_type type : List.of(Seat_type.AC1_TIER, Seat_type.EXECUTIVE_CHAIR_CAR, Seat_type.AC2_TIER,
				Seat_type.AC3_TIER, Seat_type.AC3_ECONOMY, Seat_type.AC_CHAIR_CAR, Seat_type.SLEEPER_CLASS,
				Seat_type.SECOND_CLASS)) {
			int n = counts.getOrDefault(type, 0);
			for (int i = 1; i <= n; i++) {
				Coach coach = new Coach();
				coach.setTrain(train);
				coach.setCoachType(type.name());
				coach.setCoachNumber(CoachLayout.prefix(type) + i);
				coach.setNumberOfSeats(CoachLayout.capacity(type));
				newCoaches.add(coach);
			}
		}
		coach_Repository.saveAll(newCoaches);

		List<CoachSeat> seats = new ArrayList<>();
		for (Coach coach : newCoaches) {
			Seat_type type = Seat_type.valueOf(coach.getCoachType());
			for (int seatNo = 1; seatNo <= coach.getNumberOfSeats(); seatNo++) {
				CoachSeat seat = new CoachSeat();
				seat.setCoach(coach);
				seat.setSeatNumber(seatNo);
				seat.setBerthType(CoachLayout.berthType(type, seatNo));
				seats.add(seat);
			}
		}
		coachSeat_Repository.saveAll(seats);

		if (train.getCoaches() == null) {
			train.setCoaches(new ArrayList<>());
		}
		train.getCoaches().addAll(newCoaches);

		structure.setData2(train);
		structure.setMessage(newCoaches.size() + " coaches with " + seats.size() + " seats added");
		structure.setStatus(HttpStatus.OK.value());
		return new ResponseEntity<>(structure, HttpStatus.OK);
	}

	@Override
	public ResponseEntity<ResponseStructure<List<Train>>> listTrains(String token) {
		ResponseStructure<List<Train>> structure = new ResponseStructure<>();
		if (!isAdmin(token)) {
			structure.setMessage("Invalid or Expired Token, Please Login Again");
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		}
		List<Train> trains = train_Repository.findAll(org.springframework.data.domain.Sort.by("trainNumber"));
		structure.setData2(trains);
		structure.setMessage(trains.size() + " trains");
		structure.setStatus(HttpStatus.OK.value());
		return new ResponseEntity<>(structure, HttpStatus.OK);
	}

	@Override
	public ResponseEntity<ResponseStructure<StationMaster>> addStationMaster(StationMaster station, String token) {
		ResponseStructure<StationMaster> structure = new ResponseStructure<>();
		if (!isAdmin(token)) {
			structure.setMessage("Invalid or Expired Token, Please Login Again");
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		}
		String code = station.getCode() == null ? "" : station.getCode().trim().toUpperCase();
		String name = station.getName() == null ? "" : station.getName().trim().replaceAll("\\s+", " ");
		String city = station.getCity() == null ? "" : station.getCity().trim().replaceAll("\\s+", " ");
		String problem = !code.matches("[A-Z]{2,5}") ? "Station code must be 2 to 5 capital letters, e.g. SBC"
				: name.length() < 3 || name.length() > 80 ? "Station name must be 3 to 80 characters"
						: city.length() < 2 || city.length() > 60 ? "Enter the city" : null;
		Double lat = station.getLatitude();
		Double lng = station.getLongitude();
		if (problem == null && (lat == null) != (lng == null)) {
			problem = "Enter both latitude and longitude, or neither";
		}
		if (problem == null && lat != null && (lat < -90 || lat > 90 || lng < -180 || lng > 180)) {
			problem = "Latitude must be between -90 and 90 and longitude between -180 and 180";
		}
		if (problem == null && stationMaster_Repository.existsById(code)) {
			problem = "Station code " + code + " already exists";
		}
		if (problem == null && stationMaster_Repository.existsByNameIgnoreCase(name)) {
			problem = "A station named " + name + " already exists";
		}
		if (problem != null) {
			structure.setMessage(problem);
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		}
		StationMaster saved = stationMaster_Repository
				.save(new StationMaster(code, name, city, station.getState() == null ? null : station.getState().trim(), lat, lng));
		structure.setData2(saved);
		structure.setMessage("Station " + code + " added");
		structure.setStatus(HttpStatus.CREATED.value());
		return new ResponseEntity<>(structure, HttpStatus.CREATED);
	}

	@Override
	public ResponseEntity<ResponseStructure<List<Integer>>> distances(List<String> codes, String token) {
		ResponseStructure<List<Integer>> structure = new ResponseStructure<>();
		if (!isAdmin(token)) {
			structure.setMessage("Invalid or Expired Token, Please Login Again");
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		}
		if (codes == null || codes.size() > 200) {
			structure.setMessage("Send between 1 and 200 station codes");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		}
		structure.setData2(stationDirectory.legKm(codes));
		structure.setMessage("Estimated km between stops");
		structure.setStatus(HttpStatus.OK.value());
		return new ResponseEntity<>(structure, HttpStatus.OK);
	}

	/** Valid, unexpired token whose role is admin */
	private boolean isAdmin(String token) {
		String[] subject = jwtUtil.getSubject(token);
		return subject != null && "admin".equals(subject[1]);
	}

	/**
	 * Deletes a train and everything that belongs to it, in an order the foreign keys allow.
	 * Runs inside delete()'s transaction, so it either all happens or nothing does.
	 * Uses JPQL bulk statements so Hibernate supplies the real table and column names.
	 */
	private void deleteTrainRows(int trainNo, Train train) {
		List<Long> stationIds = train.getStations() == null ? List.of()
				: train.getStations().stream().map(Station::getStationId).toList();
		List<Integer> coachIds = train.getCoaches() == null ? List.of()
				: train.getCoaches().stream().map(Coach::getId).toList();
		entityManager.clear(); // work only with bulk statements from here on

		// Train -> route / schedule references go first (they point both ways)
		entityManager.createQuery("update Train t set t.route = null, t.schedule = null where t.trainNumber = :n")
				.setParameter("n", trainNo).executeUpdate();
		if (!stationIds.isEmpty()) {
			entityManager.createQuery("update Station s set s.trains = null, s.route = null where s.stationId in :ids")
					.setParameter("ids", stationIds).executeUpdate();
		}
		entityManager.createQuery("delete from Route r where r.train.trainNumber = :n").setParameter("n", trainNo)
				.executeUpdate();
		entityManager.createQuery("delete from Schedule s where s.train.trainNumber = :n").setParameter("n", trainNo)
				.executeUpdate();
		if (!coachIds.isEmpty()) {
			coachSeat_Repository.deleteByCoachIds(coachIds);
		}
		entityManager.createQuery("delete from Coach c where c.train.trainNumber = :n").setParameter("n", trainNo)
				.executeUpdate();
		entityManager.createQuery("delete from WaitlistCounter w where w.trainNumber = :n").setParameter("n", trainNo)
				.executeUpdate();
		// Deleting the train also clears its train_stations join rows
		entityManager.createQuery("delete from Train t where t.trainNumber = :n").setParameter("n", trainNo)
				.executeUpdate();
		if (!stationIds.isEmpty()) {
			entityManager.createQuery("delete from Station s where s.stationId in :ids").setParameter("ids", stationIds)
					.executeUpdate();
		}
	}

	@Override
	@Transactional
	public ResponseEntity<ResponseStructure<Train>> delete(int train_No, String token) {
		ResponseStructure<Train> structure = new ResponseStructure<>();

		if (!isAdmin(token)) {
			structure.setData(null);
			structure.setMessage("Invalid or Expired Token, Please Login Again");
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		} else {
			Train train = train_Repository.findByTrainNumber(train_No);
			if (train == null) {
				structure.setStatus(HttpStatus.NOT_FOUND.value());
				structure.setMessage("Train Not Found");
				return new ResponseEntity<>(structure, HttpStatus.NOT_FOUND);
			}

			if (booking_Repository.existsByTrainNumber(train_No)) {
				structure.setStatus(HttpStatus.CONFLICT.value());
				structure.setMessage("This train has bookings, so it can't be deleted");
				return new ResponseEntity<>(structure, HttpStatus.CONFLICT);
			}

			try {
				deleteTrainRows(train_No, train);
				structure.setStatus(HttpStatus.OK.value());
				structure.setMessage("Train deleted successfully");
				return new ResponseEntity<>(structure, HttpStatus.OK);

			} catch (Exception e) {
				// Undo every statement above: nothing is deleted unless all of it succeeds
				TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
				structure.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
				structure.setMessage("Error deleting train: " + e.getMessage());
				return new ResponseEntity<>(structure, HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}

	}

}