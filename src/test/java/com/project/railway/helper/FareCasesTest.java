package com.project.railway.helper;

import static com.project.railway.testsupport.CaseTable.in;
import static com.project.railway.helper.Seat_type.*;

import org.junit.jupiter.api.Test;

import com.project.railway.testsupport.CaseTable;

/** Fares: rate per km, reservation charge, fare per passenger (with the 50 km minimum) */
class FareCasesTest {

	@Test
	void ratesAndReservationCharges() {
		CaseTable t = CaseTable.of("Fares", "Rate per km and reservation charge for every class");
		Object[][] cases = {
				{ SECOND_CLASS, 0.45, 15.0 }, { SLEEPER_CLASS, 0.70, 20.0 }, { AC3_TIER, 1.80, 40.0 }, { AC2_TIER, 2.60, 50.0 },
				{ AC1_TIER, 4.40, 60.0 }, { AC_CHAIR_CAR, 1.40, 40.0 }, { AC3_ECONOMY, 1.65, 40.0 }, { EXECUTIVE_CHAIR_CAR, 2.80, 60.0 } };
		for (Object[] c : cases) {
			Seat_type type = (Seat_type) c[0];
			String cls = CoachLayout.shortCode(type);
			t.check(cls + " rate per km", in("class", cls), c[1], () -> FareCalculator.ratePerKm(type));
			t.check(cls + " reservation charge", in("class", cls), c[2], () -> FareCalculator.reservationCharge(type));
		}
		t.done();
	}

	@Test
	void farePerPassenger() {
		CaseTable t = CaseTable.of("Fares", "Fare per passenger = base fare + rate x km + reservation charge");
		Object[][] cases = {
				// name, class, km, base fare, expected
				{ "Sleeper, 100 km, no base fare", SLEEPER_CLASS, 100.0, 0.0, 90.0 },
				{ "Sleeper, 100 km, base fare 100", SLEEPER_CLASS, 100.0, 100.0, 190.0 },
				{ "Sleeper, 10 km: charged for the 50 km minimum", SLEEPER_CLASS, 10.0, 0.0, 55.0 },
				{ "Sleeper, 0 km: charged for 50 km", SLEEPER_CLASS, 0.0, 0.0, 55.0 },
				{ "Sleeper, exactly 50 km", SLEEPER_CLASS, 50.0, 0.0, 55.0 },
				{ "Negative base fare counts as 0", SLEEPER_CLASS, 100.0, -50.0, 90.0 },
				{ "3A Bengaluru - Hubballi, 469 km", AC3_TIER, 469.0, 0.0, 884.0 },
				{ "2A, 50 km", AC2_TIER, 50.0, 0.0, 180.0 },
				{ "1A, 1000 km", AC1_TIER, 1000.0, 0.0, 4460.0 },
				{ "CC Vande Bharat SBC - DWR, 490 km", AC_CHAIR_CAR, 490.0, 0.0, 726.0 },
				{ "EC Vande Bharat SBC - DWR, 490 km", EXECUTIVE_CHAIR_CAR, 490.0, 0.0, 1432.0 },
				{ "3E, 300 km", AC3_ECONOMY, 300.0, 0.0, 535.0 },
				{ "2S, 0 km: 37.5 rounds up to 38", SECOND_CLASS, 0.0, 0.0, 38.0 },
				{ "2S, 211 km with base fare 20", SECOND_CLASS, 211.0, 20.0, 130.0 },
				{ "Fractional km: 3A 100.4 km", AC3_TIER, 100.4, 0.0, 221.0 } };
		for (Object[] c : cases) {
			Seat_type type = (Seat_type) c[1];
			double km = (Double) c[2];
			double base = (Double) c[3];
			t.check((String) c[0], in("class", CoachLayout.shortCode(type), "km", km, "base fare", base), c[4],
					() -> FareCalculator.perPassenger(type, km, base));
		}
		t.done();
	}
}
