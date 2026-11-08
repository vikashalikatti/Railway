package com.project.railway.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Plain unit tests for coach layouts, seat picking and fares (no database needed) */
class SeatAllocationRulesTest {

	private static List<SeatPicker.Seat> coach(String coachNo, Seat_type type, long firstId) {
		List<SeatPicker.Seat> seats = new ArrayList<>();
		for (int n = 1; n <= CoachLayout.capacity(type); n++) {
			seats.add(new SeatPicker.Seat(firstId + n, coachNo, n, CoachLayout.berthType(type, n),
					CoachLayout.bay(type, n)));
		}
		return seats;
	}

	@Test
	void sleeperCoachHasRealBerthPattern() {
		assertEquals(72, CoachLayout.capacity(Seat_type.SLEEPER_CLASS));
		assertEquals("LOWER", CoachLayout.berthType(Seat_type.SLEEPER_CLASS, 1));
		assertEquals("MIDDLE", CoachLayout.berthType(Seat_type.SLEEPER_CLASS, 2));
		assertEquals("UPPER", CoachLayout.berthType(Seat_type.SLEEPER_CLASS, 3));
		assertEquals("SIDE_LOWER", CoachLayout.berthType(Seat_type.SLEEPER_CLASS, 7));
		assertEquals("SIDE_UPPER", CoachLayout.berthType(Seat_type.SLEEPER_CLASS, 8));
		assertEquals("LOWER", CoachLayout.berthType(Seat_type.SLEEPER_CLASS, 9));
		Map<String, Integer> counts = SeatPicker.countByBerth(coach("S1", Seat_type.SLEEPER_CLASS, 0));
		assertEquals(Integer.valueOf(18), counts.get("LOWER"));
		assertEquals(Integer.valueOf(9), counts.get("SIDE_LOWER"));
	}

	@Test
	void everyClassHasAPrefixAndLayout() {
		for (Seat_type t : Seat_type.values()) {
			assertFalse(CoachLayout.prefix(t).isEmpty());
			for (int n = 1; n <= CoachLayout.capacity(t); n++) {
				assertTrue(CoachLayout.preferences(t).contains(CoachLayout.berthType(t, n)), t + " seat " + n);
			}
		}
	}

	@Test
	void seniorGetsLowerBerth() {
		List<SeatPicker.Seat> seats = coach("S1", Seat_type.SLEEPER_CLASS, 0).subList(1, 6); // MIDDLE, UPPER, LOWER, MIDDLE, UPPER
		Map<Integer, SeatPicker.Seat> result = SeatPicker.assign(List.of(new SeatPicker.Traveller(0, 67, null)), seats);
		assertEquals("LOWER", result.get(0).berthType());
	}

	@Test
	void preferenceIsHonoured() {
		List<SeatPicker.Seat> seats = coach("S1", Seat_type.SLEEPER_CLASS, 0).subList(0, 8);
		Map<Integer, SeatPicker.Seat> result = SeatPicker
				.assign(List.of(new SeatPicker.Traveller(0, 25, "SIDE_UPPER")), seats);
		assertEquals("SIDE_UPPER", result.get(0).berthType());
	}

	@Test
	void groupStaysInOneBayAndCoach() {
		List<SeatPicker.Seat> seats = new ArrayList<>(coach("S1", Seat_type.SLEEPER_CLASS, 0).subList(0, 16));
		seats.addAll(coach("S2", Seat_type.SLEEPER_CLASS, 100).subList(0, 4));
		List<SeatPicker.Traveller> family = List.of(new SeatPicker.Traveller(0, 40, null),
				new SeatPicker.Traveller(1, 38, null), new SeatPicker.Traveller(2, 12, null),
				new SeatPicker.Traveller(3, 9, null));
		Map<Integer, SeatPicker.Seat> result = SeatPicker.assign(family, seats);
		assertEquals(4, result.size());
		Set<String> coaches = new HashSet<>();
		Set<Integer> bays = new HashSet<>();
		result.values().forEach(s -> {
			coaches.add(s.coachNumber());
			bays.add(s.bay());
		});
		assertEquals(Set.of("S1"), coaches);
		assertEquals(1, bays.size());
	}

	@Test
	void noSeatIsGivenTwiceAndExtraPassengersGetNone() {
		List<SeatPicker.Seat> seats = coach("B1", Seat_type.AC3_TIER, 0).subList(0, 3);
		List<SeatPicker.Traveller> travellers = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			travellers.add(new SeatPicker.Traveller(i, 30, null));
		}
		Map<Integer, SeatPicker.Seat> result = SeatPicker.assign(travellers, seats);
		assertEquals(3, result.size());
		assertEquals(3, new HashSet<>(result.values()).size());
	}

	@Test
	void pickCoachPrefersOneThatFitsTheGroup() {
		Map<String, Integer> free = new java.util.LinkedHashMap<>();
		free.put("S1", 3);
		free.put("S2", 10);
		free.put("S3", 6);
		assertEquals("S2", SeatPicker.pickCoach(free, 4));
		free.put("S2", 2);
		assertEquals("S3", SeatPicker.pickCoach(free, 4));
		assertEquals("S3", SeatPicker.pickCoach(free, 9)); // none fits: emptiest
	}

	@Test
	void fareGrowsWithDistanceAndClass() {
		double sl = FareCalculator.perPassenger(Seat_type.SLEEPER_CLASS, 380, 0);
		double ac3 = FareCalculator.perPassenger(Seat_type.AC3_TIER, 380, 0);
		assertEquals(286, sl); // 0.70 x 380 + 20
		assertTrue(ac3 > sl);
		assertEquals(FareCalculator.perPassenger(Seat_type.SLEEPER_CLASS, 10, 0),
				FareCalculator.perPassenger(Seat_type.SLEEPER_CLASS, 50, 0)); // minimum 50 km
	}

	@Test
	void chairCarsAndThreeEconomyHaveRealLayouts() {
		// CC is 3+2: window, middle, aisle | aisle, window
		assertEquals("WINDOW", CoachLayout.berthType(Seat_type.AC_CHAIR_CAR, 1));
		assertEquals("AISLE", CoachLayout.berthType(Seat_type.AC_CHAIR_CAR, 4));
		assertEquals("WINDOW", CoachLayout.berthType(Seat_type.AC_CHAIR_CAR, 5));
		// EC is 2+2 with no middle seat
		assertFalse(CoachLayout.preferences(Seat_type.EXECUTIVE_CHAIR_CAR).contains("MIDDLE_SEAT"));
		// 3E has three side berths per bay
		assertEquals("SIDE_MIDDLE", CoachLayout.berthType(Seat_type.AC3_ECONOMY, 8));
		assertEquals("LOWER", CoachLayout.berthType(Seat_type.AC3_ECONOMY, 10));
		assertEquals("C", CoachLayout.prefix(Seat_type.AC_CHAIR_CAR));
		assertEquals("E", CoachLayout.prefix(Seat_type.EXECUTIVE_CHAIR_CAR));
		assertEquals("M", CoachLayout.prefix(Seat_type.AC3_ECONOMY));
		// Fares sit in the usual order
		double sl = FareCalculator.perPassenger(Seat_type.SLEEPER_CLASS, 300, 0);
		double cc = FareCalculator.perPassenger(Seat_type.AC_CHAIR_CAR, 300, 0);
		double e3 = FareCalculator.perPassenger(Seat_type.AC3_ECONOMY, 300, 0);
		double a3 = FareCalculator.perPassenger(Seat_type.AC3_TIER, 300, 0);
		double ec = FareCalculator.perPassenger(Seat_type.EXECUTIVE_CHAIR_CAR, 300, 0);
		double a1 = FareCalculator.perPassenger(Seat_type.AC1_TIER, 300, 0);
		assertTrue(sl < cc && cc < e3 && e3 < a3 && a3 < ec && ec < a1);
	}
}
