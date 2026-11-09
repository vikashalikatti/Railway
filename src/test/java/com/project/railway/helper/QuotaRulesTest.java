package com.project.railway.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class QuotaRulesTest {

	@Test
	void tatkalOpensTheDayBeforeAtTenForAcAndElevenForSleeper() {
		LocalDate journey = LocalDate.of(2026, 11, 10);
		assertEquals(LocalDateTime.of(2026, 11, 9, 10, 0), TatkalRules.opensAt(journey, Seat_type.AC3_TIER));
		assertEquals(LocalDateTime.of(2026, 11, 9, 11, 0), TatkalRules.opensAt(journey, Seat_type.SLEEPER_CLASS));
		assertEquals(LocalDateTime.of(2026, 11, 9, 11, 0), TatkalRules.opensAt(journey, Seat_type.SECOND_CLASS));
		assertEquals(LocalDateTime.of(2026, 11, 9, 10, 0), TatkalRules.opensAt(journey, Seat_type.AC_CHAIR_CAR));
	}

	@Test
	void tatkalChargeStaysBetweenMinimumAndMaximum() {
		assertEquals(100, TatkalRules.charge(Seat_type.SLEEPER_CLASS, 200)); // 30% = 60 -> min 100
		assertEquals(150, TatkalRules.charge(Seat_type.SLEEPER_CLASS, 500)); // 30% = 150
		assertEquals(200, TatkalRules.charge(Seat_type.SLEEPER_CLASS, 2000)); // 600 -> max 200
		assertEquals(15, TatkalRules.charge(Seat_type.SECOND_CLASS, 400)); // 10% = 40 -> max 15
		assertEquals(0, TatkalRules.charge(Seat_type.AC1_TIER, 3000)); // no Tatkal in 1A
	}

	@Test
	void premiumTatkalRisesAsSeatsSell() {
		assertEquals(1.0, TatkalRules.premiumFactor(0, 20));
		assertEquals(1.5, TatkalRules.premiumFactor(10, 20));
		assertEquals(2.0, TatkalRules.premiumFactor(20, 20));
		double cheap = TatkalRules.fare(Quota.PT, Seat_type.SLEEPER_CLASS, 500, 0, 20);
		double dear = TatkalRules.fare(Quota.PT, Seat_type.SLEEPER_CLASS, 500, 15, 20);
		assertTrue(dear > cheap);
		assertEquals(650, TatkalRules.fare(Quota.TQ, Seat_type.SLEEPER_CLASS, 500, 0, 0));
		assertEquals(500, TatkalRules.fare(Quota.GN, Seat_type.SLEEPER_CLASS, 500, 0, 0));
	}

	@Test
	void chartTimes() {
		LocalDateTime dep = LocalDateTime.of(2026, 11, 10, 6, 0);
		assertEquals(LocalDateTime.of(2026, 11, 10, 2, 0), TatkalRules.firstChartAt(dep));
		assertEquals(LocalDateTime.of(2026, 11, 10, 5, 30), TatkalRules.finalChartAt(dep));
	}

	@Test
	void quotaEligibility() {
		List<Quota.Traveller> women = List.of(new Quota.Traveller(30, "Female"), new Quota.Traveller(8, "Male"));
		assertNull(Quota.LD.eligibility(women)); // a boy under 12 may travel with her
		assertNotNull(Quota.LD.eligibility(List.of(new Quota.Traveller(30, "Male"))));
		assertNotNull(Quota.LD.eligibility(List.of(new Quota.Traveller(8, "Female")))); // no adult woman

		assertNull(Quota.SS.eligibility(List.of(new Quota.Traveller(61, "Male"), new Quota.Traveller(46, "Female"))));
		assertNotNull(Quota.SS.eligibility(List.of(new Quota.Traveller(59, "Male"))));
		assertNotNull(Quota.SS.eligibility(List.of(new Quota.Traveller(70, "Male"), new Quota.Traveller(70, "Male"),
				new Quota.Traveller(70, "Female")))); // max 2

		List<Quota.Traveller> five = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			five.add(new Quota.Traveller(30, "Male"));
		}
		assertNotNull(Quota.TQ.eligibility(five)); // Tatkal: max 4
		assertNull(Quota.GN.eligibility(five));
	}

	@Test
	void quotaOfferedPerClass() {
		assertFalse(Quota.TQ.offeredIn(Seat_type.AC1_TIER));
		assertFalse(Quota.SS.offeredIn(Seat_type.AC_CHAIR_CAR));
		assertTrue(Quota.SS.offeredIn(Seat_type.SLEEPER_CLASS));
		assertEquals("SLEEPER_CLASS", Quota.GN.counterKey(Seat_type.SLEEPER_CLASS));
		assertEquals("TQ:EC", Quota.TQ.counterKey(Seat_type.EXECUTIVE_CHAIR_CAR));
		assertTrue(Quota.TQ.counterKey(Seat_type.EXECUTIVE_CHAIR_CAR).length() <= 20); // fits the column
	}

	@Test
	void plannerSplitsSeatsByShare() {
		List<QuotaPlanner.SeatRef> seats = new ArrayList<>();
		long id = 1;
		for (int coach = 1; coach <= 2; coach++) {
			for (int n = 1; n <= 72; n++) {
				seats.add(new QuotaPlanner.SeatRef(id++, coach, n, CoachLayout.berthType(Seat_type.SLEEPER_CLASS, n)));
			}
		}
		Map<Long, Quota> plan = QuotaPlanner.plan(seats, new QuotaPlanner.Shares(30, 10, 5, 8));
		long tq = plan.values().stream().filter(q -> q == Quota.TQ).count();
		long pt = plan.values().stream().filter(q -> q == Quota.PT).count();
		long ld = plan.values().stream().filter(q -> q == Quota.LD).count();
		long ss = plan.values().stream().filter(q -> q == Quota.SS).count();
		assertEquals(144 * 30 / 100, tq);
		assertEquals(144 * 10 / 100, pt);
		assertEquals(144 * 5 / 100, ld);
		assertEquals(144 * 8 / 100, ss);
		// senior seats are lower berths, in both coaches
		for (QuotaPlanner.SeatRef s : seats) {
			if (plan.get(s.id()) == Quota.SS) {
				assertTrue(CoachLayout.isLower(s.berthType()));
			}
		}
		assertTrue(seats.stream().anyMatch(s -> s.coachId() == 1 && plan.get(s.id()) == Quota.SS));
		assertTrue(seats.stream().anyMatch(s -> s.coachId() == 2 && plan.get(s.id()) == Quota.SS));
		assertNotNull(new QuotaPlanner.Shares(50, 30, 10, 10).problem(Seat_type.SLEEPER_CLASS)); // > 90%
		assertNotNull(new QuotaPlanner.Shares(10, 0, 0, 0).problem(Seat_type.AC1_TIER)); // no Tatkal in 1A
		assertNull(QuotaPlanner.Shares.defaults(Seat_type.AC_CHAIR_CAR).problem(Seat_type.AC_CHAIR_CAR));
		assertNull(QuotaPlanner.Shares.defaults(Seat_type.AC1_TIER).problem(Seat_type.AC1_TIER));
	}
}
