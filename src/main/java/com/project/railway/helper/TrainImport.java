package com.project.railway.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads train lists for the admin's bulk import. Plain Java (no Spring, no database) so it can be
 * unit-tested.
 *
 * Three sources:
 *  1. The bundled Indian Railways list (data/indian-railways.json.gz): compact arrays
 *     {"stations":[[code,name,city,state,lat,lon]], "trains":[[number,name,type,[classes],[[code,arr,dep,km]]]]}
 *  2. A JSON file the admin uploads:
 *     {"stations":[{"code","name","city","state","latitude","longitude"}],
 *      "trains":[{"number","name","type","days":["monday",…],"classes":["SL","3A"],
 *                 "coaches":{"SL":6,"3A":2},"stops":[{"code","name","arrival","departure","km"}]}]}
 *  3. A CSV file, one row per stop (train columns only needed on a train's first row):
 *     train_number,train_name,train_type,running_days,classes,station_code,station_name,arrival,departure,km[,day]
 *  km is from the first station; day (1, 2, 3…) is optional and worked out from the times when left out
 *
 * Problems with one train never stop the others: the train is left out and the problem is listed.
 */
public final class TrainImport {

	private TrainImport() {
	}

	public static final int MAX_TRAINS = 10000;
	public static final int MAX_PROBLEMS = 50;
	public static final String CSV_HEADER = "train_number,train_name,train_type,running_days,classes,station_code,station_name,arrival,departure,km";

	public static final List<String> ALL_DAYS = List.of("monday", "tuesday", "wednesday", "thursday", "friday",
			"saturday", "sunday");

	public record ImportStation(String code, String name, String city, String state, Double latitude, Double longitude) {
	}

	/** km from the first station, may be null (estimated from station positions); day may be null (from the times) */
	public record ImportStop(String code, String name, LocalTime arrival, LocalTime departure, Double km, Integer day) {
		public ImportStop(String code, String name, LocalTime arrival, LocalTime departure, Double km) {
			this(code, name, arrival, departure, km, null);
		}
	}

	/** coaches: null means "use the import's default count for each of the classes" */
	public record ImportTrain(int number, String name, String type, List<String> days, List<Seat_type> classes,
			Map<Seat_type, Integer> coaches, List<ImportStop> stops) {
	}

	public record Batch(String source, List<ImportStation> stations, List<ImportTrain> trains, List<String> problems) {
	}

	/* ------------------------------------------------------------------ bundled list */

	public static Batch readBundled(InputStream gz) throws IOException {
		JsonNode root = new ObjectMapper().readTree(new InputStreamReader(new GZIPInputStream(gz), StandardCharsets.UTF_8));
		List<ImportStation> stations = new ArrayList<>();
		for (JsonNode s : root.path("stations")) {
			stations.add(new ImportStation(s.get(0).asText(), s.get(1).asText(), s.get(2).asText(), text(s.get(3)),
					number(s.get(4)), number(s.get(5))));
		}
		List<ImportTrain> trains = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		for (JsonNode t : root.path("trains")) {
			List<Seat_type> classes = new ArrayList<>();
			t.get(3).forEach(c -> classes.add(Seat_type.valueOf(c.asText())));
			List<ImportStop> stops = new ArrayList<>();
			for (JsonNode s : t.get(4)) {
				stops.add(new ImportStop(s.get(0).asText(), null, LocalTime.parse(s.get(1).asText()),
						LocalTime.parse(s.get(2).asText()), s.get(3).asDouble(), s.size() > 4 ? s.get(4).asInt() : null));
			}
			add(trains, problems, new ImportTrain(t.get(0).asInt(), t.get(1).asText(), t.get(2).asText(), ALL_DAYS,
					classes, null, stops));
		}
		return new Batch(root.path("source").asText("Bundled list"), stations, trains, problems);
	}

	/* ------------------------------------------------------------------ JSON upload */

	public static Batch readJson(String json) {
		List<ImportStation> stations = new ArrayList<>();
		List<ImportTrain> trains = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		JsonNode root;
		try {
			root = new ObjectMapper().readTree(json);
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			throw new IllegalArgumentException("The file isn't valid JSON: " + e.getOriginalMessage());
		}
		if (root == null || !root.path("trains").isArray()) {
			throw new IllegalArgumentException("The JSON needs a \"trains\" list (and optionally \"stations\").");
		}
		int i = 0;
		for (JsonNode s : root.path("stations")) {
			i++;
			String code = code(s.path("code").asText(""));
			String name = clean(s.path("name").asText(""), 80);
			if (code == null || name.isEmpty()) {
				problem(problems, "Station " + i + ": needs a code (1 to 6 capital letters) and a name.");
				continue;
			}
			String city = clean(s.path("city").asText(""), 60);
			stations.add(new ImportStation(code, name, city.isEmpty() ? cityOf(name) : city, blankToNull(clean(s.path("state").asText(""), 40)),
					number(s.get("latitude")), number(s.get("longitude"))));
		}
		i = 0;
		for (JsonNode t : root.path("trains")) {
			i++;
			String label = "Train " + (t.hasNonNull("number") ? t.get("number").asText() : "#" + i);
			try {
				List<ImportStop> stops = new ArrayList<>();
				int n = 0;
				for (JsonNode s : t.path("stops")) {
					n++;
					stops.add(new ImportStop(code(s.path("code").asText("")), blankToNull(clean(s.path("name").asText(""), 80)),
							time(s.get("arrival"), "stop " + n + " arrival"), time(s.get("departure"), "stop " + n + " departure"),
							s.hasNonNull("km") ? s.get("km").asDouble() : null, s.hasNonNull("day") ? s.get("day").asInt() : null));
				}
				List<String> days = new ArrayList<>();
				t.path("days").forEach(d -> days.add(d.asText()));
				List<Seat_type> classes = new ArrayList<>();
				t.path("classes").forEach(c -> classes.add(seatType(c.asText())));
				Map<Seat_type, Integer> coaches = null;
				if (t.path("coaches").isObject()) {
					coaches = new EnumMap<>(Seat_type.class);
					for (var it = t.path("coaches").fields(); it.hasNext();) {
						var e = it.next();
						coaches.merge(seatType(e.getKey()), e.getValue().asInt(), Integer::sum);
					}
				}
				add(trains, problems, new ImportTrain(trainNumber(t.path("number").asText("")),
						clean(t.path("name").asText(""), 80), clean(t.path("type").asText(""), 30), days(days), classes, coaches, stops));
			} catch (IllegalArgumentException e) {
				problem(problems, label + ": " + e.getMessage());
			}
		}
		return new Batch("Uploaded JSON", stations, trains, problems);
	}

	/* ------------------------------------------------------------------ CSV upload */

	public static Batch readCsv(String csv) {
		List<String> lines = csv.lines().filter(l -> !l.isBlank()).toList();
		if (lines.isEmpty()) {
			throw new IllegalArgumentException("The file is empty.");
		}
		List<String> header = split(stripBom(lines.get(0))).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
		Map<String, Integer> col = new LinkedHashMap<>();
		for (int i = 0; i < header.size(); i++) {
			col.put(header.get(i), i);
		}
		for (String needed : List.of("train_number", "station_code", "arrival", "departure")) {
			if (!col.containsKey(needed)) {
				throw new IllegalArgumentException("The first row must be the column names: " + CSV_HEADER);
			}
		}

		List<ImportTrain> trains = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		Map<Integer, String[]> head = new LinkedHashMap<>(); // number -> name, type, days, classes
		Map<Integer, List<ImportStop>> stops = new LinkedHashMap<>();
		Set<Integer> bad = new HashSet<>();
		for (int r = 1; r < lines.size(); r++) {
			List<String> f = split(lines.get(r));
			int row = r + 1;
			Integer number = null;
			try {
				number = trainNumber(get(f, col, "train_number"));
				String[] h = head.computeIfAbsent(number, k -> new String[4]);
				h[0] = first(h[0], get(f, col, "train_name"));
				h[1] = first(h[1], get(f, col, "train_type"));
				h[2] = first(h[2], get(f, col, "running_days"));
				h[3] = first(h[3], get(f, col, "classes"));
				String km = get(f, col, "km");
				String day = get(f, col, "day");
				stops.computeIfAbsent(number, k -> new ArrayList<>()).add(new ImportStop(code(get(f, col, "station_code")),
						blankToNull(clean(get(f, col, "station_name"), 80)), time(get(f, col, "arrival"), "arrival"),
						time(get(f, col, "departure"), "departure"), km.isBlank() ? null : Double.valueOf(km.trim()),
						day.isBlank() ? null : Integer.valueOf(day.trim())));
			} catch (IllegalArgumentException e) {
				problem(problems, "Row " + row + ": " + e.getMessage());
				if (number != null) {
					bad.add(number);
				}
			}
		}
		for (Map.Entry<Integer, List<ImportStop>> e : stops.entrySet()) {
			if (bad.contains(e.getKey())) {
				problem(problems, "Train " + e.getKey() + " was left out because of the rows above.");
				continue;
			}
			String[] h = head.get(e.getKey());
			try {
				List<Seat_type> classes = new ArrayList<>();
				for (String c : (h[3] == null ? "" : h[3]).split("[\\s;|/]+")) {
					if (!c.isBlank()) {
						classes.add(seatType(c));
					}
				}
				List<String> days = new ArrayList<>(List.of((h[2] == null ? "" : h[2]).split("[\\s;|/]+")));
				add(trains, problems, new ImportTrain(e.getKey(), clean(h[0], 80), clean(h[1], 30), days(days), classes, null,
						e.getValue()));
			} catch (IllegalArgumentException ex) {
				problem(problems, "Train " + e.getKey() + ": " + ex.getMessage());
			}
		}
		return new Batch("Uploaded CSV", List.of(), trains, problems);
	}

	/* ------------------------------------------------------------------ shared rules */

	/** Checks a train and adds it, or lists the problem */
	private static void add(List<ImportTrain> trains, List<String> problems, ImportTrain t) {
		String p = problem(t);
		if (p != null) {
			problem(problems, "Train " + t.number() + ": " + p);
		} else if (trains.size() >= MAX_TRAINS) {
			problem(problems, "Only the first " + MAX_TRAINS + " trains are imported at a time.");
		} else {
			trains.add(normalised(t));
		}
	}

	/** What's wrong with a train, or null */
	public static String problem(ImportTrain t) {
		if (t.name() == null || t.name().isBlank()) {
			return "needs a name";
		}
		if (t.stops() == null || t.stops().size() < 2) {
			return "needs at least 2 stops";
		}
		Set<String> seen = new HashSet<>();
		Double lastKm = null;
		for (int i = 0; i < t.stops().size(); i++) {
			ImportStop s = t.stops().get(i);
			if (s.code() == null) {
				return "stop " + (i + 1) + " needs a station code";
			}
			if (!seen.add(s.code())) {
				return s.code() + " is on the route twice";
			}
			if ((i == 0 ? s.departure() : s.arrival()) == null) {
				return "stop " + (i + 1) + " (" + s.code() + ") needs a time";
			}
			if (s.day() != null && (s.day() < 1 || s.day() > 7)) {
				return "stop " + (i + 1) + " (" + s.code() + "): day must be 1 to 7";
			}
			if (s.km() != null) {
				if (s.km() < 0 || (lastKm != null && s.km() <= lastKm && i > 0)) {
					return "km must go up along the route (stop " + (i + 1) + ", " + s.code() + ")";
				}
				lastKm = s.km();
			}
		}
		if (t.coaches() != null) {
			int total = t.coaches().values().stream().mapToInt(Integer::intValue).sum();
			if (t.coaches().values().stream().anyMatch(n -> n < 0) || total > CoachLayout.MAX_COACHES) {
				return "coaches must be 0 to " + CoachLayout.MAX_COACHES + " in total";
			}
		}
		return null;
	}

	/** First stop arrives when it departs, last stop departs when it arrives; first km is 0 */
	private static ImportTrain normalised(ImportTrain t) {
		List<ImportStop> stops = new ArrayList<>();
		for (int i = 0; i < t.stops().size(); i++) {
			ImportStop s = t.stops().get(i);
			LocalTime arr = s.arrival() != null ? s.arrival() : s.departure();
			LocalTime dep = s.departure() != null ? s.departure() : s.arrival();
			stops.add(new ImportStop(s.code(), s.name(), arr, dep, i == 0 ? Double.valueOf(0) : s.km(), s.day()));
		}
		String type = t.type() == null || t.type().isBlank() ? "Express" : t.type();
		return new ImportTrain(t.number(), t.name(), type, t.days(), t.classes(), t.coaches(), stops);
	}

	/**
	 * Fills in missing km from straight-line distances x 1.25 (positions by code, [lat, lon]);
	 * gaps without positions are spread evenly; km always goes up by at least 1.
	 */
	public static List<Double> fillKm(List<ImportStop> stops, Map<String, double[]> positions) {
		int n = stops.size();
		Double[] km = new Double[n];
		km[0] = 0.0;
		for (int i = 1; i < n; i++) {
			km[i] = stops.get(i).km();
		}
		// straight-line estimate between consecutive stops that have positions
		double[] est = new double[n];
		boolean[] known = new boolean[n];
		known[0] = true;
		double[] prev = positions.get(stops.get(0).code());
		for (int i = 1; i < n; i++) {
			double[] here = positions.get(stops.get(i).code());
			if (here != null && prev != null && known[i - 1]) {
				est[i] = est[i - 1] + straightKm(prev, here) * 1.25;
				known[i] = true;
			}
			prev = here;
		}
		for (int i = 1; i < n; i++) {
			if (km[i] == null && known[i]) {
				km[i] = Math.round(est[i]) * 1.0;
			}
		}
		// spread the rest between the nearest known values
		for (int i = 1; i < n; i++) {
			if (km[i] == null) {
				int lo = i - 1;
				int hi = i + 1;
				while (hi < n && km[hi] == null) {
					hi++;
				}
				double from = km[lo];
				double to = hi < n ? km[hi] : from + 10.0 * (hi - lo);
				for (int j = i; j < hi; j++) {
					km[j] = (double) Math.round(from + (to - from) * (j - lo) / (hi - lo));
				}
			}
		}
		List<Double> out = new ArrayList<>();
		double last = -1;
		for (int i = 0; i < n; i++) {
			double v = i == 0 ? 0 : Math.max(km[i], last + 1);
			out.add(v);
			last = v;
		}
		return out;
	}

	static double straightKm(double[] a, double[] b) {
		double r = 6371;
		double la1 = Math.toRadians(a[0]);
		double la2 = Math.toRadians(b[0]);
		double dla = la2 - la1;
		double dlo = Math.toRadians(b[1] - a[1]);
		double h = Math.sin(dla / 2) * Math.sin(dla / 2) + Math.cos(la1) * Math.cos(la2) * Math.sin(dlo / 2) * Math.sin(dlo / 2);
		return 2 * r * Math.asin(Math.sqrt(h));
	}

	/** "SL", "3A", "sleeper_class" … -> Seat_type */
	public static Seat_type seatType(String text) {
		String t = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
		for (Seat_type type : Seat_type.values()) {
			if (type.name().equals(t) || CoachLayout.shortCode(type).equals(t)) {
				return type;
			}
		}
		throw new IllegalArgumentException("unknown class " + text + " (use SL, 3A, 2A, 1A, 3E, CC, EC or 2S)");
	}

	/** "daily", "", "mon wed fri", "Monday" … -> lower-case day names in week order */
	public static List<String> days(List<String> given) {
		Set<String> picked = new HashSet<>();
		for (String d : given) {
			String x = d == null ? "" : d.trim().toLowerCase(Locale.ROOT);
			if (x.isEmpty()) {
				continue;
			}
			if (x.equals("daily") || x.equals("all")) {
				return ALL_DAYS;
			}
			String match = null;
			for (DayOfWeek w : DayOfWeek.values()) {
				String full = w.name().toLowerCase(Locale.ROOT);
				if (full.equals(x) || (x.length() >= 3 && full.startsWith(x))) {
					match = full;
				}
			}
			if (match == null) {
				throw new IllegalArgumentException("unknown running day " + d);
			}
			picked.add(match);
		}
		if (picked.isEmpty()) {
			return ALL_DAYS;
		}
		return ALL_DAYS.stream().filter(picked::contains).toList();
	}

	public static int trainNumber(String text) {
		String t = text == null ? "" : text.trim();
		if (!t.matches("\\d{1,5}") || Integer.parseInt(t) == 0) {
			throw new IllegalArgumentException("train number must be 1 to 5 digits, not \"" + t + "\"");
		}
		return Integer.parseInt(t);
	}

	/** Station code in capitals, or null when it isn't 1 to 6 letters */
	public static String code(String text) {
		String c = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
		return c.matches("[A-Z]{1,6}") ? c : null;
	}

	private static LocalTime time(JsonNode node, String what) {
		return node == null || node.isNull() ? null : time(node.asText(), what);
	}

	/** "06:40", "6:40", "06:40:00"; empty or "-" means none */
	public static LocalTime time(String text, String what) {
		String t = text == null ? "" : text.trim();
		if (t.isEmpty() || t.equals("-") || t.equalsIgnoreCase("none")) {
			return null;
		}
		if (t.matches("\\d:\\d\\d(:\\d\\d)?")) {
			t = "0" + t;
		}
		try {
			return LocalTime.parse(t.length() > 5 ? t.substring(0, 8) : t);
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException(what + " must be a time like 06:40, not \"" + text.trim() + "\"");
		}
	}

	/** "Bengaluru Cantt" -> "Bengaluru"; used when a file gives no city */
	public static String cityOf(String name) {
		String c = name.replaceAll("(?i)\\s+(jn|junction|cantt|cant|terminus|term|halt)\\.?$", "").trim();
		if (c.isEmpty()) {
			c = name;
		}
		return c.length() > 60 ? c.substring(0, 60) : c;
	}

	/** Splits one CSV line; double quotes may wrap a value and "" is a quote inside one */
	static List<String> split(String line) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (quoted) {
				if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					cur.append('"');
					i++;
				} else if (ch == '"') {
					quoted = false;
				} else {
					cur.append(ch);
				}
			} else if (ch == '"') {
				quoted = true;
			} else if (ch == ',') {
				out.add(cur.toString());
				cur.setLength(0);
			} else {
				cur.append(ch);
			}
		}
		out.add(cur.toString());
		return out;
	}

	private static String get(List<String> f, Map<String, Integer> col, String name) {
		Integer i = col.get(name);
		return i == null || i >= f.size() ? "" : f.get(i).trim();
	}

	private static String first(String kept, String value) {
		return kept != null && !kept.isBlank() ? kept : value;
	}

	private static String stripBom(String s) {
		return s.startsWith("\uFEFF") ? s.substring(1) : s;
	}

	private static String clean(String s, int max) {
		String c = s == null ? "" : s.trim().replaceAll("\\s+", " ").replaceAll("[<>]", "");
		return c.length() > max ? c.substring(0, max) : c;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}

	private static String text(JsonNode n) {
		return n == null || n.isNull() ? null : n.asText();
	}

	private static Double number(JsonNode n) {
		return n == null || n.isNull() || !n.isNumber() ? null : n.asDouble();
	}

	private static void problem(List<String> problems, String p) {
		if (problems.size() < MAX_PROBLEMS) {
			problems.add(p);
		}
	}
}
