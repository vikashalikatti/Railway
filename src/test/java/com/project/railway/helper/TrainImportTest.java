package com.project.railway.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TrainImportTest {

	@Test
	void csvWithTrainColumnsOnTheFirstRowOnly() {
		String csv = TrainImport.CSV_HEADER + "\n"
				+ "12627,Karnataka Express,Superfast,daily,SL 3A 2A,SBC,,,19:20,0\n"
				+ "12627,,,,,DMM,,21:00,21:02,\n"
				+ "12627,,,,,NDLS,New Delhi,10:35,,2400\n"
				+ "16535,\"Gol Gumbaz, Express\",Express,mon wed fri,SL,YPR,,19:45,19:45,0\n"
				+ "16535,,,,,SUR,,13:25,13:25,822\n";
		TrainImport.Batch b = TrainImport.readCsv(csv);
		assertEquals(List.of(), b.problems());
		assertEquals(2, b.trains().size());
		TrainImport.ImportTrain k = b.trains().get(0);
		assertEquals(12627, k.number());
		assertEquals(List.of(Seat_type.SLEEPER_CLASS, Seat_type.AC3_TIER, Seat_type.AC2_TIER), k.classes());
		assertEquals(TrainImport.ALL_DAYS, k.days());
		assertEquals(LocalTime.of(19, 20), k.stops().get(0).arrival()); // first stop arrives when it leaves
		assertEquals(LocalTime.of(10, 35), k.stops().get(2).departure()); // last stop leaves when it arrives
		assertNull(k.stops().get(1).km());
		assertEquals("New Delhi", k.stops().get(2).name());
		TrainImport.ImportTrain g = b.trains().get(1);
		assertEquals("Gol Gumbaz, Express", g.name());
		assertEquals(List.of("monday", "wednesday", "friday"), g.days());
	}

	@Test
	void badRowsLeaveOutOnlyTheirTrain() {
		String csv = TrainImport.CSV_HEADER + "\n"
				+ "111,A,,,,SBC,,,06:00,0\n111,,,,,UBL,,25:99,,400\n"
				+ "222,B,,,,SBC,,,06:00,0\n222,,,,,SBC,,08:00,,100\n"
				+ "333,C,,,,SBC,,,06:00,0\n333,,,,,MYS,,08:00,,140\n";
		TrainImport.Batch b = TrainImport.readCsv(csv);
		assertEquals(1, b.trains().size());
		assertEquals(333, b.trains().get(0).number());
		assertTrue(b.problems().stream().anyMatch(p -> p.contains("Row 3")));
		assertTrue(b.problems().stream().anyMatch(p -> p.contains("SBC is on the route twice")));
	}

	@Test
	void jsonUpload() {
		String json = "{\"stations\":[{\"code\":\"xyz\",\"name\":\"Kengeri Halt\",\"latitude\":12.5,\"longitude\":77.1}],"
				+ "\"trains\":[{\"number\":\"99001\",\"name\":\"Test Express\",\"days\":[\"Sat\",\"sunday\"],"
				+ "\"coaches\":{\"SL\":5,\"3A\":1},\"stops\":[{\"code\":\"SBC\",\"departure\":\"06:00\"},"
				+ "{\"code\":\"XYZ\",\"arrival\":\"07:00\",\"departure\":\"07:05\",\"km\":60}]},"
				+ "{\"number\":\"abc\",\"name\":\"Bad\"}]}";
		TrainImport.Batch b = TrainImport.readJson(json);
		assertEquals(1, b.trains().size());
		assertEquals("XYZ", b.stations().get(0).code());
		assertEquals("Kengeri", b.stations().get(0).city());
		TrainImport.ImportTrain t = b.trains().get(0);
		assertEquals(List.of("saturday", "sunday"), t.days());
		assertEquals(Map.of(Seat_type.SLEEPER_CLASS, 5, Seat_type.AC3_TIER, 1), t.coaches());
		assertEquals(1, b.problems().size());
	}

	@Test
	void missingKmComesFromPositionsAndAlwaysGoesUp() {
		List<TrainImport.ImportStop> stops = List.of(
				new TrainImport.ImportStop("SBC", null, null, LocalTime.of(6, 0), 0.0),
				new TrainImport.ImportStop("TK", null, LocalTime.of(7, 0), LocalTime.of(7, 2), null),
				new TrainImport.ImportStop("QQ", null, LocalTime.of(8, 0), LocalTime.of(8, 2), null), // no position
				new TrainImport.ImportStop("UBL", null, LocalTime.of(12, 0), LocalTime.of(12, 0), 469.0));
		Map<String, double[]> pos = Map.of("SBC", new double[] { 12.9781, 77.5695 }, "TK", new double[] { 13.3409, 77.101 },
				"UBL", new double[] { 15.3466, 75.1424 });
		List<Double> km = TrainImport.fillKm(stops, pos);
		assertEquals(0.0, km.get(0));
		assertTrue(km.get(1) > 60 && km.get(1) < 90, "SBC-TK is about 70 km by rail: " + km.get(1));
		assertTrue(km.get(2) > km.get(1) && km.get(2) < 469);
		assertEquals(469.0, km.get(3));
	}

	@Test
	void namesAndCodes() {
		assertEquals(Seat_type.AC3_TIER, TrainImport.seatType("3a"));
		assertEquals(Seat_type.SLEEPER_CLASS, TrainImport.seatType("SLEEPER_CLASS"));
		assertNull(TrainImport.code("SB1"));
		assertEquals("Hubballi", TrainImport.cityOf("Hubballi Jn"));
		assertEquals(LocalTime.of(6, 5), TrainImport.time("6:05", "x"));
	}

	@Test
	void bundledIndianRailwaysList() throws Exception {
		try (InputStream in = TrainImportTest.class.getResourceAsStream("/data/indian-railways.json.gz")) {
			assertNotNull(in, "data/indian-railways.json.gz is on the classpath");
			TrainImport.Batch b = TrainImport.readBundled(in);
			assertTrue(b.trains().size() > 2000, "trains: " + b.trains().size());
			assertTrue(b.stations().size() > 3000);
			assertEquals(List.of(), b.problems());
			TrainImport.ImportTrain k = b.trains().stream().filter(t -> t.number() == 12627).findFirst().orElseThrow();
			assertEquals("SBC", k.stops().get(0).code());
			assertEquals("NDLS", k.stops().get(k.stops().size() - 1).code());
			// Karnataka Express reaches New Delhi on Day 3, and the times agree with the day numbers
			assertEquals(Integer.valueOf(3), k.stops().get(k.stops().size() - 1).day());
			var tl = StopDays.timeline(k.stops().stream().map(s -> new StopDays.Stop(s.arrival(), s.departure(), null)).toList());
			assertEquals(3, tl.day(k.stops().size() - 1));
			for (TrainImport.ImportTrain t : b.trains()) {
				assertNull(TrainImport.problem(t), "train " + t.number());
			}
		}
	}
}
