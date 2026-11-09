package com.project.railway.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** RAC, day numbers, refunds, waitlist chance, test payments and rate limiting */
class RailRulesTest {

	private static StopDays.Stop stop(String arr, String dep, Integer day) {
		return new StopDays.Stop(arr == null ? null : LocalTime.parse(arr), dep == null ? null : LocalTime.parse(dep), day);
	}

	@Test
	void daysAreWorkedOutFromTheTimes() {
		// Karnataka Express style: leaves 19:20, passes midnight, arrives Day 3
		var t = StopDays.timeline(List.of(stop(null, "19:20", null), stop("23:50", "23:55", null), stop("04:10", "04:15", null),
				stop("18:00", "18:05", null), stop("10:35", null, null)));
		assertEquals(1, t.day(0));
		assertEquals(1, t.day(1));
		assertEquals(2, t.day(2));
		assertEquals(2, t.day(3));
		assertEquals(3, t.day(4));
		LocalDate run = LocalDate.of(2026, 12, 1);
		assertEquals(LocalDate.of(2026, 12, 2), t.boardingDate(run, 2));
		assertEquals(run, t.runDate(LocalDate.of(2026, 12, 2), 2));
		assertEquals(LocalDate.of(2026, 12, 3).atTime(10, 35), t.arrives(run, 4));
		assertEquals((24 + 15) * 60 + 15, t.minutes(0, 4)); // 39 h 15 min
	}

	@Test
	void givenDaysWinAndDepartureAfterMidnightCounts() {
		// a long halt across midnight: arrives 23:40, leaves 00:20 the next day
		var t = StopDays.timeline(List.of(stop(null, "22:00", 1), stop("23:40", "00:20", 1), stop("06:00", null, 3)));
		assertEquals(1, t.day(1));
		assertEquals(2, t.departureDay(1));
		assertEquals(3, t.day(2)); // the timetable says Day 3, though the times alone would say Day 2
	}

	@Test
	void sameDayTrainsStayOnDayOne() {
		var t = StopDays.timeline(List.of(stop(null, "06:00", null), stop("07:10", "07:12", null), stop("13:30", null, null)));
		assertEquals(1, t.day(2));
		assertEquals(450, t.minutes(0, 2));
	}

	@Test
	void racRoomCountsTheBusiestStop() {
		var booked = List.of(new RacRules.Stretch(0, 3), new RacRules.Stretch(2, 5), new RacRules.Stretch(3, 5));
		assertEquals(3, RacRules.room(4, booked, 0, 2)); // only one RAC passenger on stops 0-1
		assertEquals(2, RacRules.room(4, booked, 2, 4)); // two at stops 2, 3 and 4
		assertEquals(0, RacRules.room(2, booked, 3, 5));
	}

	@Test
	void racPassengersShareBerthsInPairs() {
		List<RacRules.Berth> berths = new ArrayList<>(List.of(new RacRules.Berth(1), new RacRules.Berth(2)));
		berths.get(1).blocked.add(new RacRules.Stretch(0, 2)); // someone confirmed on berth 2 for stops 0-2
		int[] at = RacRules.place(List.of(new RacRules.Stretch(0, 4), new RacRules.Stretch(0, 4), new RacRules.Stretch(1, 3),
				new RacRules.Stretch(2, 4)), berths);
		assertEquals(0, at[0]);
		assertEquals(0, at[1]); // shares berth 1 with the first
		assertEquals(-1, at[2]); // berth 1 is full and berth 2 is taken on stop 1
		assertEquals(1, at[3]);
		assertEquals(4, RacRules.maxBerthsPerCoach(Seat_type.AC2_TIER) > 0 ? 4 : 0);
		assertTrue(RacRules.maxBerthsPerCoach(Seat_type.SLEEPER_CLASS) >= 4);
		assertFalse(RacRules.hasRac(Seat_type.AC_CHAIR_CAR));
	}

	@Test
	void quotaPlanKeepsSideLowersForRacAndLowersForDivyangjan() {
		List<QuotaPlanner.SeatRef> seats = new ArrayList<>();
		for (int coach = 1; coach <= 2; coach++) {
			for (int n = 1; n <= 72; n++) {
				seats.add(new QuotaPlanner.SeatRef(coach * 100 + n, coach, n, CoachLayout.berthType(Seat_type.SLEEPER_CLASS, n)));
			}
		}
		Map<Long, Quota> plan = QuotaPlanner.plan(seats, QuotaPlanner.Shares.defaults(Seat_type.SLEEPER_CLASS));
		long rac = plan.values().stream().filter(q -> q == Quota.RC).count();
		assertEquals(8, rac); // 4 per coach
		seats.stream().filter(s -> plan.get(s.id()) == Quota.RC)
				.forEach(s -> assertEquals(CoachLayout.SIDE_LOWER, s.berthType()));
		seats.stream().filter(s -> plan.get(s.id()) == Quota.PH)
				.forEach(s -> assertEquals(CoachLayout.LOWER, s.berthType()));
		assertTrue(plan.values().stream().filter(q -> q == Quota.PH).count() >= 2);
		assertEquals(0, QuotaPlanner.plan(seats, new QuotaPlanner.Shares(30, 10, 5, 8)).values().stream()
				.filter(q -> q == Quota.RC).count());
	}

	@Test
	void divyangjanQuotaNeedsACardHolder() {
		assertNotNull(Quota.PH.eligibility(List.of(new Quota.Traveller(40, "Male"), new Quota.Traveller(38, "Female"))));
		assertNull(Quota.PH.eligibility(List.of(new Quota.Traveller(40, "Male", true), new Quota.Traveller(38, "Female"))));
		assertNotNull(Quota.PH.eligibility(List.of(new Quota.Traveller(40, "Male", true), new Quota.Traveller(38, "Female"),
				new Quota.Traveller(12, "Male"))));
		assertFalse(Quota.RC.bookable());
	}

	@Test
	void cancellationCharges() {
		var far = RefundRules.onCancel(RefundRules.Held.CONFIRMED, Quota.GN, Seat_type.SLEEPER_CLASS, 500, 120, 72 * 60, false);
		assertEquals(120.0, far.charge());
		assertEquals(500.0, far.refund()); // 500 - 120 + meal 120
		var mid = RefundRules.onCancel(RefundRules.Held.CONFIRMED, Quota.GN, Seat_type.AC3_TIER, 1200, 0, 24 * 60, false);
		assertEquals(300.0, mid.charge()); // 25%
		var late = RefundRules.onCancel(RefundRules.Held.CONFIRMED, Quota.GN, Seat_type.AC3_TIER, 1200, 0, 6 * 60, false);
		assertEquals(600.0, late.charge()); // 50%
		var cheap = RefundRules.onCancel(RefundRules.Held.CONFIRMED, Quota.GN, Seat_type.AC3_TIER, 400, 0, 6 * 60, false);
		assertEquals(200.0, cheap.refund()); // 50% (200) is more than the flat 180
		var charted = RefundRules.onCancel(RefundRules.Held.CONFIRMED, Quota.GN, Seat_type.AC3_TIER, 1200, 150, 3 * 60, true);
		assertEquals(0.0, charted.refund());
		var wl = RefundRules.onCancel(RefundRules.Held.WAITLISTED, Quota.GN, Seat_type.SLEEPER_CLASS, 500, 0, 3 * 60, true);
		assertEquals(440.0, wl.refund());
		var tatkal = RefundRules.onCancel(RefundRules.Held.CONFIRMED, Quota.TQ, Seat_type.SLEEPER_CLASS, 700, 0, 20 * 60, false);
		assertEquals(0.0, tatkal.refund());
		var gone = RefundRules.onCancel(RefundRules.Held.RAC, Quota.GN, Seat_type.SLEEPER_CLASS, 500, 0, 10, true);
		assertEquals(0.0, gone.refund());
		assertEquals(560.0, RefundRules.droppedAtChart(500, 120).refund());
		assertEquals(620.0, RefundRules.trainCancelled(500, 120).refund());
	}

	@Test
	void waitlistChance() {
		assertEquals("High", WaitlistChance.estimate(1, 432, 8, 0, 0).label());
		assertEquals("Low", WaitlistChance.estimate(60, 432, 0, 0, 0).label());
		assertEquals(80, WaitlistChance.estimate(30, 432, 0, 50, 40).percent()); // history wins
	}

	@Test
	void testPayments() {
		assertTrue(PaymentRules.luhn("4111 1111 1111 1111"));
		assertFalse(PaymentRules.luhn("4111 1111 1111 1112"));
		assertTrue(PaymentRules.luhn(PaymentRules.DECLINED_CARD));
		YearMonth now = YearMonth.of(2026, 11);
		assertNull(PaymentRules.cardProblem("4111111111111111", "12/28", "123", now));
		assertNotNull(PaymentRules.cardProblem("4111111111111111", "10/26", "123", now));
		assertNotNull(PaymentRules.cardProblem("4111111111111111", "12/28", "12", now));
		assertEquals("•••• 1111", PaymentRules.mask("4111-1111-1111-1111"));
		assertEquals("Visa", PaymentRules.brand("4111111111111111"));
		assertTrue(PaymentRules.validUpi("vikas@okaxis"));
		assertFalse(PaymentRules.validUpi("vikas"));
	}

	@Test
	void rateLimit() {
		RateLimiter r = new RateLimiter(3);
		assertTrue(r.allow("a", 0));
		assertTrue(r.allow("a", 1));
		assertTrue(r.allow("a", 2));
		assertFalse(r.allow("a", 3));
		assertTrue(r.allow("b", 3)); // another client
		assertTrue(r.allow("a", 20_003)); // one token back after 20 s
	}
}
