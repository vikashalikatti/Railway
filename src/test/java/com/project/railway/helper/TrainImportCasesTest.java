package com.project.railway.helper;

import static com.project.railway.testsupport.CaseTable.in;

import java.io.InputStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.project.railway.helper.TrainImport.ImportStop;
import com.project.railway.helper.TrainImport.ImportTrain;
import com.project.railway.testsupport.CaseTable;

/** Importing trains: reading each field, checking a train, CSV and JSON files, and the Karnataka list */
class TrainImportCasesTest {

	@Test
	void fields() {
		CaseTable t = CaseTable.of("Train import", "Reading train numbers, station codes, times, classes and days");
		t.check("Train number", in("text", "12627"), 12627, () -> TrainImport.trainNumber("12627"));
		t.check("Train number with spaces", in("text", " 7 "), 7, () -> TrainImport.trainNumber(" 7 "));
		for (String bad : new String[] { "0", "123456", "12a", "", "-5" }) {
			t.checkThrows("Bad train number \"" + bad + "\"", in("text", bad), IllegalArgumentException.class, "train number must be 1 to 5 digits",
					() -> TrainImport.trainNumber(bad));
		}
		Object[][] codes = { { "sbc", "SBC" }, { " ypr ", "YPR" }, { "A", "A" }, { "SBC1", null }, { "ABCDEFG", null }, { "", null }, { null, null } };
		for (Object[] c : codes) {
			String v = (String) c[0];
			t.check("Station code " + (v == null ? "(missing)" : "\"" + v + "\""), in("text", v), c[1], () -> TrainImport.code(v));
		}
		Object[][] times = { { "06:40", LocalTime.of(6, 40) }, { "6:40", LocalTime.of(6, 40) }, { "06:40:00", LocalTime.of(6, 40) },
				{ "23:59", LocalTime.of(23, 59) }, { "-", null }, { "", null }, { "none", null } };
		for (Object[] c : times) {
			String v = (String) c[0];
			t.check("Time \"" + v + "\"", in("text", v), c[1], () -> TrainImport.time(v, "arrival"));
		}
		for (String bad : new String[] { "25:00", "6.40", "noon" }) {
			t.checkThrows("Bad time \"" + bad + "\"", in("text", bad), IllegalArgumentException.class, "arrival must be a time like 06:40",
					() -> TrainImport.time(bad, "arrival"));
		}
		Object[][] classes = { { "SL", Seat_type.SLEEPER_CLASS }, { "3a", Seat_type.AC3_TIER }, { "sleeper_class", Seat_type.SLEEPER_CLASS },
				{ " CC ", Seat_type.AC_CHAIR_CAR }, { "EXECUTIVE_CHAIR_CAR", Seat_type.EXECUTIVE_CHAIR_CAR }, { "2S", Seat_type.SECOND_CLASS } };
		for (Object[] c : classes) {
			String v = (String) c[0];
			t.check("Class \"" + v + "\"", in("text", v), c[1], () -> TrainImport.seatType(v));
		}
		t.checkThrows("Unknown class", in("text", "XX"), IllegalArgumentException.class, "unknown class XX", () -> TrainImport.seatType("XX"));
		List<String> all = TrainImport.ALL_DAYS;
		t.check("Days: daily", in("days", "daily"), all, () -> TrainImport.days(List.of("daily")));
		t.check("Days: none given means daily", in("days", "(empty)"), all, () -> TrainImport.days(List.of("")));
		t.check("Days: mon wed fri", in("days", "mon wed fri"), List.of("monday", "wednesday", "friday"),
				() -> TrainImport.days(List.of("mon", "wed", "fri")));
		t.check("Days come back in week order", in("days", "Sunday Monday"), List.of("monday", "sunday"),
				() -> TrainImport.days(List.of("Sunday", "Monday")));
		t.checkThrows("Days: 'mo' is too short", in("days", "mo"), IllegalArgumentException.class, "unknown running day", () -> TrainImport.days(List.of("mo")));
		t.checkThrows("Days: 'funday'", in("days", "funday"), IllegalArgumentException.class, "unknown running day funday",
				() -> TrainImport.days(List.of("funday")));
		Object[][] cities = { { "Bengaluru Cantt", "Bengaluru" }, { "Hubballi Jn.", "Hubballi" }, { "Mysuru Junction", "Mysuru" }, { "Mysuru", "Mysuru" },
				{ "Halt", "Halt" } };
		for (Object[] c : cities) {
			String v = (String) c[0];
			t.check("City of \"" + v + "\"", in("station name", v), c[1], () -> TrainImport.cityOf(v));
		}
		t.check("CSV line with quotes and commas", in("line", "12627,\"Karnataka, Express\",SF"), List.of("12627", "Karnataka, Express", "SF"),
				() -> TrainImport.split("12627,\"Karnataka, Express\",SF"));
		t.check("CSV line with a quote inside quotes", in("line", "1,\"say \"\"hi\"\"\""), List.of("1", "say \"hi\""),
				() -> TrainImport.split("1,\"say \"\"hi\"\"\""));
		t.done();
	}

	private static ImportStop stop(String code, String arr, String dep, Double km, Integer day) {
		return new ImportStop(code, null, arr == null ? null : LocalTime.parse(arr), dep == null ? null : LocalTime.parse(dep), km, day);
	}

	private static ImportTrain train(String name, List<ImportStop> stops, Map<Seat_type, Integer> coaches) {
		return new ImportTrain(1, name, "Express", TrainImport.ALL_DAYS, List.of(Seat_type.SLEEPER_CLASS), coaches, stops);
	}

	@Test
	void checkingATrain() {
		CaseTable t = CaseTable.of("Train import", "Checking a train before it's imported (null = fine)");
		List<ImportStop> ok = List.of(stop("SBC", null, "06:00", 0.0, 1), stop("TK", "07:00", "07:02", 70.0, 1), stop("DVG", "10:00", null, 326.0, 1));
		Object[][] cases = {
				{ "Good train", train("Test Express", ok, null), null },
				{ "No name", train(" ", ok, null), "needs a name" },
				{ "One stop", train("X", List.of(stop("SBC", null, "06:00", 0.0, 1)), null), "needs at least 2 stops" },
				{ "Missing station code", train("X", List.of(stop("SBC", null, "06:00", 0.0, 1), stop(null, "07:00", null, 70.0, 1)), null),
						"stop 2 needs a station code" },
				{ "Station twice", train("X", List.of(stop("SBC", null, "06:00", 0.0, 1), stop("SBC", "07:00", null, 70.0, 1)), null),
						"SBC is on the route twice" },
				{ "First stop without departure", train("X", List.of(stop("SBC", "06:00", null, 0.0, 1), stop("TK", "07:00", null, 70.0, 1)), null),
						"stop 1 (SBC) needs a time" },
				{ "Later stop without arrival", train("X", List.of(stop("SBC", null, "06:00", 0.0, 1), stop("TK", null, "07:00", 70.0, 1)), null),
						"stop 2 (TK) needs a time" },
				{ "Day 8", train("X", List.of(stop("SBC", null, "06:00", 0.0, 1), stop("TK", "07:00", null, 70.0, 8)), null),
						"stop 2 (TK): day must be 1 to 7" },
				{ "km going down", train("X", List.of(stop("SBC", null, "06:00", 0.0, 1), stop("TK", "07:00", "07:02", 70.0, 1),
						stop("DVG", "10:00", null, 60.0, 1)), null), "km must go up along the route (stop 3, DVG)" },
				{ "Negative km", train("X", List.of(stop("SBC", null, "06:00", -1.0, 1), stop("TK", "07:00", null, 70.0, 1)), null),
						"km must go up along the route (stop 1, SBC)" },
				{ "Missing km is fine", train("X", List.of(stop("SBC", null, "06:00", null, null), stop("TK", "07:00", null, null, null)), null), null },
				{ "25 coaches", train("X", ok, Map.of(Seat_type.SLEEPER_CLASS, 20, Seat_type.AC3_TIER, 5)), "coaches must be 0 to 24 in total" },
				{ "Negative coaches", train("X", ok, Map.of(Seat_type.SLEEPER_CLASS, -1)), "coaches must be 0 to 24 in total" },
				{ "24 coaches is the most", train("X", ok, Map.of(Seat_type.SLEEPER_CLASS, 24)), null } };
		for (Object[] c : cases) {
			ImportTrain tr = (ImportTrain) c[1];
			t.check((String) c[0], in("train", describe(tr)), c[2], () -> TrainImport.problem(tr));
		}
		t.done();
	}

	private static String describe(ImportTrain t) {
		List<String> s = new ArrayList<>();
		t.stops().forEach(x -> s.add(x.code() + " " + (x.arrival() == null ? "--" : x.arrival()) + "/" + (x.departure() == null ? "--" : x.departure())
				+ (x.km() == null ? "" : " " + x.km().intValue() + "km") + (x.day() == null ? "" : " d" + x.day())));
		return "\"" + t.name() + "\": " + String.join(", ", s) + (t.coaches() == null ? "" : "; coaches " + t.coaches());
	}

	@Test
	void filesAndKm() {
		CaseTable t = CaseTable.of("Train import", "CSV and JSON files, and km filled in from station positions");
		String header = "train_number,train_name,train_type,running_days,classes,station_code,station_name,arrival,departure,km\n";
		String good = header + "16535,Gol Gumbaz Express,Express,daily,SL 3A,YPR,Yesvantpur,,19:45,0\n16535,,,,,TK,Tumakuru,20:33,20:35,65\n";
		t.check("Good CSV: one train", in("file", "2 rows of train 16535"), 1, () -> TrainImport.readCsv(good).trains().size());
		t.check("Good CSV: its classes", in("file", "classes 'SL 3A'"), List.of(Seat_type.SLEEPER_CLASS, Seat_type.AC3_TIER),
				() -> TrainImport.readCsv(good).trains().get(0).classes());
		t.check("Good CSV: first stop arrives when it departs", in("file", "YPR with no arrival"), LocalTime.of(19, 45),
				() -> TrainImport.readCsv(good).trains().get(0).stops().get(0).arrival());
		t.checkThrows("Empty CSV", in("file", "(empty)"), IllegalArgumentException.class, "The file is empty.", () -> TrainImport.readCsv("  \n"));
		t.checkThrows("CSV without the column names", in("file", "data only"), IllegalArgumentException.class, "The first row must be the column names",
				() -> TrainImport.readCsv("16535,YPR,19:45\n"));
		String badRow = header + "16535,Gol Gumbaz,Express,daily,SL,YPR,,,19:45,0\n16535,,,,,TK,,8 pm,20:35,65\n";
		t.check("Bad time in a row: train left out", in("file", "arrival '8 pm'"), 0, () -> TrainImport.readCsv(badRow).trains().size());
		t.checkThat("Bad time in a row: the problem names the row", in("file", "arrival '8 pm'"), "a problem starting \"Row 3:\"",
				() -> TrainImport.readCsv(badRow).problems(), p -> !p.isEmpty() && p.get(0).startsWith("Row 3:"));
		t.checkThrows("JSON that isn't JSON", in("file", "{oops"), IllegalArgumentException.class, "isn't valid JSON", () -> TrainImport.readJson("{oops"));
		t.checkThrows("JSON without trains", in("file", "{\"stations\":[]}"), IllegalArgumentException.class, "needs a \"trains\" list",
				() -> TrainImport.readJson("{\"stations\":[]}"));
		String json = "{\"trains\":[{\"number\":20661,\"name\":\"Vande Bharat\",\"days\":[\"mon\"],\"classes\":[\"CC\"],"
				+ "\"stops\":[{\"code\":\"SBC\",\"departure\":\"05:45\",\"km\":0},{\"code\":\"DWR\",\"arrival\":\"12:00\",\"km\":490}]}]}";
		t.check("Good JSON: running days", in("file", "Vande Bharat, days [mon]"), List.of("monday"), () -> TrainImport.readJson(json).trains().get(0).days());

		List<ImportStop> stops = List.of(stop("A", null, "06:00", null, null), stop("B", "07:00", null, null, null));
		Map<String, double[]> pos = Map.of("A", new double[] { 12.9776, 77.5681 }, "B", new double[] { 13.3330, 77.1024 });
		t.checkThat("km from positions: straight line x 1.25", in("A", "Bengaluru 12.9776,77.5681", "B", "Tumakuru 13.3330,77.1024"),
				"about 79 km (63 km straight x 1.25)", () -> TrainImport.fillKm(stops, pos).get(1), km -> km >= 75 && km <= 83);
		t.check("km without positions: 10 km per stop", in("stops", 3, "positions", "none"), List.of(0.0, 10.0, 20.0),
				() -> TrainImport.fillKm(List.of(stop("A", null, "06:00", null, null), stop("B", "07:00", "07:05", null, null),
						stop("C", "08:00", null, null, null)), Map.of()));
		t.check("Gap between known km is spread evenly", in("km", "0, ?, 100"), List.of(0.0, 50.0, 100.0),
				() -> TrainImport.fillKm(List.of(stop("A", null, "06:00", 0.0, null), stop("B", "07:00", "07:05", null, null),
						stop("C", "08:00", null, 100.0, null)), Map.of()));
		t.check("km always goes up by at least 1", in("km", "0, 5, 5"), List.of(0.0, 5.0, 6.0),
				() -> TrainImport.fillKm(List.of(stop("A", null, "06:00", 0.0, null), stop("B", "07:00", "07:05", 5.0, null),
						stop("C", "08:00", null, 5.0, null)), Map.of()));
		t.done();
	}

	@Test
	void karnatakaList() throws Exception {
		CaseTable t = CaseTable.of("Train import", "The bundled Karnataka train list");
		TrainImport.Batch b;
		try (InputStream in = TrainImportCasesTest.class.getResourceAsStream("/data/karnataka-railways.json.gz")) {
			b = in == null ? null : TrainImport.readBundled(in);
		}
		TrainImport.Batch batch = b;
		t.check("The list is in the build", in("file", "data/karnataka-railways.json.gz"), true, () -> batch != null);
		if (batch == null) {
			t.done();
			return;
		}
		t.checkThat("At least 400 trains", in("list", batch.source()), "400 or more", () -> batch.trains().size(), n -> n >= 400);
		t.checkThat("At least 400 Karnataka stations", in("list", batch.source()), "400 or more",
				() -> batch.stations().stream().filter(s -> "Karnataka".equals(s.state())).count(), n -> n >= 400);
		t.check("No problems reading it", in("list", batch.source()), List.of(), () -> batch.problems());
		t.check("12 Vande Bharat trains", in("type", "Vande Bharat"), 12L,
				() -> batch.trains().stream().filter(x -> "Vande Bharat".equals(x.type())).count());
		for (int no : new int[] { 20661, 20662, 26751, 26752, 20607, 20608, 22231, 22232, 20645, 20646, 20669, 20670 }) {
			t.checkThat("Vande Bharat " + no + " is there with CC coaches", in("train", no), "a Vande Bharat with chair car coaches",
					() -> batch.trains().stream().filter(x -> x.number() == no).findFirst().orElse(null),
					x -> x != null && "Vande Bharat".equals(x.type()) && x.coaches() != null && x.coaches().getOrDefault(Seat_type.AC_CHAIR_CAR, 0) > 0);
		}
		t.check("20661 runs 6 days (not Tuesday)", in("train", 20661), List.of("monday", "wednesday", "thursday", "friday", "saturday", "sunday"),
				() -> batch.trains().stream().filter(x -> x.number() == 20661).findFirst().orElseThrow().days());
		t.check("Every train passes the import check", in("trains", batch.trains().size()), List.of(), () -> {
			List<String> bad = new ArrayList<>();
			batch.trains().forEach(x -> {
				String p = TrainImport.problem(x);
				if (p != null) {
					bad.add(x.number() + ": " + p);
				}
			});
			return bad;
		});
		t.check("Day numbers agree with the times on every train", in("trains", batch.trains().size()), List.of(), () -> {
			List<Integer> bad = new ArrayList<>();
			for (ImportTrain x : batch.trains()) {
				var tl = StopDays.timeline(x.stops().stream().map(s -> new StopDays.Stop(s.arrival(), s.departure(), null)).toList());
				int last = x.stops().size() - 1;
				if (x.stops().get(last).day() != null && x.stops().get(last).day() != tl.day(last)) {
					bad.add(x.number());
				}
			}
			return bad;
		});
		t.done();
	}
}
