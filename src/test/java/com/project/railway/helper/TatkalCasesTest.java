package com.project.railway.helper;

import static com.project.railway.testsupport.CaseTable.in;
import static com.project.railway.helper.Seat_type.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.project.railway.testsupport.CaseTable;

/** Tatkal and Premium Tatkal: opening times, charges, price steps; chart times */
class TatkalCasesTest {

	@Test
	void openingTimes() {
		CaseTable t = CaseTable.of("Tatkal", "Tatkal opens the day before: AC at 10:00, Sleeper and 2S at 11:00");
		LocalDate journey = LocalDate.of(2026, 12, 10);
		Object[][] cases = { { AC1_TIER, true, "10:00" }, { AC2_TIER, true, "10:00" }, { AC3_TIER, true, "10:00" },
				{ AC3_ECONOMY, true, "10:00" }, { AC_CHAIR_CAR, true, "10:00" }, { EXECUTIVE_CHAIR_CAR, true, "10:00" },
				{ SLEEPER_CLASS, false, "11:00" }, { SECOND_CLASS, false, "11:00" } };
		for (Object[] c : cases) {
			Seat_type type = (Seat_type) c[0];
			String cls = CoachLayout.shortCode(type);
			t.check(cls + " is an AC class?", in("class", cls), c[1], () -> TatkalRules.isAc(type));
			t.check(cls + " opens", in("class", cls, "journey date", journey), LocalDateTime.parse("2026-12-09T" + c[2]),
					() -> TatkalRules.opensAt(journey, type));
		}
		t.check("Journey on 1 January opens on 31 December", in("class", "SL", "journey date", "2027-01-01"),
				LocalDateTime.parse("2026-12-31T11:00"), () -> TatkalRules.opensAt(LocalDate.of(2027, 1, 1), SLEEPER_CLASS));
		t.done();
	}

	@Test
	void tatkalCharge() {
		CaseTable t = CaseTable.of("Tatkal", "Tatkal charge: a share of the fare between a minimum and a maximum");
		Object[][] cases = {
				// class, fare, expected charge, why
				{ SLEEPER_CLASS, 200.0, 100.0, "30% = 60, raised to the minimum 100" },
				{ SLEEPER_CLASS, 500.0, 150.0, "30% = 150" },
				{ SLEEPER_CLASS, 1000.0, 200.0, "30% = 300, cut to the maximum 200" },
				{ SECOND_CLASS, 50.0, 10.0, "10% = 5, raised to 10" },
				{ SECOND_CLASS, 120.0, 12.0, "10% = 12" },
				{ SECOND_CLASS, 300.0, 15.0, "10% = 30, cut to 15" },
				{ AC_CHAIR_CAR, 400.0, 125.0, "30% = 120, raised to 125" },
				{ AC_CHAIR_CAR, 600.0, 180.0, "30% = 180" },
				{ AC_CHAIR_CAR, 1000.0, 225.0, "30% = 300, cut to 225" },
				{ AC3_TIER, 900.0, 300.0, "30% = 270, raised to 300" },
				{ AC3_TIER, 1200.0, 360.0, "30% = 360" },
				{ AC3_TIER, 2000.0, 400.0, "30% = 600, cut to 400" },
				{ AC3_ECONOMY, 1200.0, 360.0, "same as 3A" },
				{ AC2_TIER, 1000.0, 400.0, "30% = 300, raised to 400" },
				{ AC2_TIER, 1500.0, 450.0, "30% = 450" },
				{ AC2_TIER, 3000.0, 500.0, "30% = 900, cut to 500" },
				{ EXECUTIVE_CHAIR_CAR, 1500.0, 450.0, "same as 2A" },
				{ AC1_TIER, 4000.0, 0.0, "no Tatkal in AC First Class" } };
		for (Object[] c : cases) {
			Seat_type type = (Seat_type) c[0];
			double fare = (Double) c[1];
			t.check(CoachLayout.shortCode(type) + ": " + c[3], in("class", CoachLayout.shortCode(type), "fare", fare), c[2],
					() -> TatkalRules.charge(type, fare));
		}
		t.done();
	}

	@Test
	void premiumTatkalAndQuotaFares() {
		CaseTable t = CaseTable.of("Tatkal", "Premium Tatkal price steps and the fare in each quota");
		long[][] steps = { { 0, 10 }, { 3, 10 }, { 5, 10 }, { 10, 10 }, { 15, 10 }, { 1, 3 }, { 5, 0 } };
		double[] factors = { 1.0, 1.3, 1.5, 2.0, 2.0, 1.3, 1.0 };
		for (int i = 0; i < steps.length; i++) {
			long sold = steps[i][0];
			long total = steps[i][1];
			t.check("Price factor with " + sold + " of " + total + " sold", in("sold", sold, "total", total), factors[i],
					() -> TatkalRules.premiumFactor(sold, total));
		}
		t.check("General: fare unchanged", in("quota", "GN", "class", "SL", "fare", 500), 500.0,
				() -> TatkalRules.fare(Quota.GN, SLEEPER_CLASS, 500, 0, 0));
		t.check("Ladies: fare unchanged", in("quota", "LD", "class", "SL", "fare", 500), 500.0,
				() -> TatkalRules.fare(Quota.LD, SLEEPER_CLASS, 500, 0, 0));
		t.check("Tatkal: fare + Tatkal charge", in("quota", "TQ", "class", "SL", "fare", 500), 650.0,
				() -> TatkalRules.fare(Quota.TQ, SLEEPER_CLASS, 500, 0, 0));
		t.check("Premium Tatkal, nothing sold yet: like Tatkal", in("quota", "PT", "class", "SL", "fare", 500, "sold", "0 of 10"),
				650.0, () -> TatkalRules.fare(Quota.PT, SLEEPER_CLASS, 500, 0, 10));
		t.check("Premium Tatkal, half sold: x1.5", in("quota", "PT", "class", "SL", "fare", 500, "sold", "5 of 10"), 975.0,
				() -> TatkalRules.fare(Quota.PT, SLEEPER_CLASS, 500, 5, 10));
		t.check("Premium Tatkal, last seats: x2", in("quota", "PT", "class", "3A", "fare", 1200, "sold", "10 of 10"), 3120.0,
				() -> TatkalRules.fare(Quota.PT, AC3_TIER, 1200, 10, 10));
		t.done();
	}

	@Test
	void chartTimes() {
		CaseTable t = CaseTable.of("Tatkal", "Charts: first chart 4 hours, final chart 30 minutes before departure");
		LocalDateTime dep = LocalDateTime.parse("2026-12-10T06:00");
		t.check("First chart", in("departure", dep), LocalDateTime.parse("2026-12-10T02:00"), () -> TatkalRules.firstChartAt(dep));
		t.check("Final chart", in("departure", dep), LocalDateTime.parse("2026-12-10T05:30"), () -> TatkalRules.finalChartAt(dep));
		LocalDateTime early = LocalDateTime.parse("2026-12-10T01:30");
		t.check("First chart the evening before an early train", in("departure", early), LocalDateTime.parse("2026-12-09T21:30"),
				() -> TatkalRules.firstChartAt(early));
		LocalDateTime midnight = LocalDateTime.parse("2026-12-10T00:15");
		t.check("Final chart before midnight", in("departure", midnight), LocalDateTime.parse("2026-12-09T23:45"),
				() -> TatkalRules.finalChartAt(midnight));
		t.done();
	}
}
