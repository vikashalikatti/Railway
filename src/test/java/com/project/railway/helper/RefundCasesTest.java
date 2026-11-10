package com.project.railway.helper;

import static com.project.railway.testsupport.CaseTable.in;
import static com.project.railway.helper.Seat_type.*;

import org.junit.jupiter.api.Test;

import com.project.railway.helper.RefundRules.Held;
import com.project.railway.testsupport.CaseTable;

/** Cancellation charges and refunds */
class RefundCasesTest {

	@Test
	void flatCharges() {
		CaseTable t = CaseTable.of("Refunds", "Flat cancellation charge per passenger (confirmed, 48+ hours before)");
		Object[][] cases = { { AC1_TIER, 240.0 }, { EXECUTIVE_CHAIR_CAR, 240.0 }, { AC2_TIER, 200.0 }, { AC3_TIER, 180.0 },
				{ AC3_ECONOMY, 180.0 }, { AC_CHAIR_CAR, 180.0 }, { SLEEPER_CLASS, 120.0 }, { SECOND_CLASS, 60.0 } };
		for (Object[] c : cases) {
			Seat_type type = (Seat_type) c[0];
			t.check(CoachLayout.shortCode(type), in("class", CoachLayout.shortCode(type)), c[1], () -> RefundRules.flatCharge(type));
		}
		t.done();
	}

	/** name, held, quota, class, fare, extras, minutes before, chart prepared, expected refund, expected charge */
	private static final Object[][] CANCEL = {
			{ "Confirmed SL, 50 h before: flat charge", Held.CONFIRMED, Quota.GN, SLEEPER_CLASS, 500.0, 0.0, 50 * 60L, false, 380.0, 120.0 },
			{ "Confirmed SL, exactly 48 h before: 25%", Held.CONFIRMED, Quota.GN, SLEEPER_CLASS, 500.0, 0.0, 48 * 60L, false, 375.0, 125.0 },
			{ "Confirmed 3A, 24 h before: 25%", Held.CONFIRMED, Quota.GN, AC3_TIER, 1000.0, 0.0, 24 * 60L, false, 750.0, 250.0 },
			{ "Confirmed 3A, 13 h before: 25%", Held.CONFIRMED, Quota.GN, AC3_TIER, 1000.0, 0.0, 13 * 60L, false, 750.0, 250.0 },
			{ "Confirmed 3A, exactly 12 h before: 50%", Held.CONFIRMED, Quota.GN, AC3_TIER, 1000.0, 0.0, 12 * 60L, false, 500.0, 500.0 },
			{ "Confirmed SL, 6 h before: 50%", Held.CONFIRMED, Quota.GN, SLEEPER_CLASS, 500.0, 0.0, 6 * 60L, false, 250.0, 250.0 },
			{ "Confirmed SL fare 200, 1 h before: 50% is less than the flat 120", Held.CONFIRMED, Quota.GN, SLEEPER_CLASS, 200.0, 0.0, 60L, false, 80.0, 120.0 },
			{ "Confirmed 2A, 25% is less than the flat 200", Held.CONFIRMED, Quota.GN, AC2_TIER, 600.0, 0.0, 24 * 60L, false, 400.0, 200.0 },
			{ "Confirmed 2S fare 40: charge can't be more than the fare", Held.CONFIRMED, Quota.GN, SECOND_CLASS, 40.0, 0.0, 72 * 60L, false, 0.0, 40.0 },
			{ "Confirmed SL with meal and bedroll 270: extras back", Held.CONFIRMED, Quota.GN, SLEEPER_CLASS, 500.0, 270.0, 50 * 60L, false, 650.0, 120.0 },
			{ "Confirmed, exactly 30 minutes before: still 50%", Held.CONFIRMED, Quota.GN, SLEEPER_CLASS, 500.0, 0.0, 30L, false, 250.0, 250.0 },
			{ "Confirmed, 29 minutes before: no refund", Held.CONFIRMED, Quota.GN, SLEEPER_CLASS, 500.0, 120.0, 29L, false, 0.0, 620.0 },
			{ "After the train left: no refund", Held.CONFIRMED, Quota.GN, SLEEPER_CLASS, 500.0, 0.0, -10L, true, 0.0, 500.0 },
			{ "Confirmed, chart prepared: no refund, extras kept too", Held.CONFIRMED, Quota.GN, SLEEPER_CLASS, 500.0, 150.0, 3 * 60L, true, 0.0, 650.0 },
			{ "Ladies quota confirmed: same as General", Held.CONFIRMED, Quota.LD, SLEEPER_CLASS, 500.0, 0.0, 50 * 60L, false, 380.0, 120.0 },
			{ "Senior quota confirmed: same as General", Held.CONFIRMED, Quota.SS, AC3_TIER, 1000.0, 0.0, 50 * 60L, false, 820.0, 180.0 },
			{ "RAC: fare minus clerkage 60", Held.RAC, Quota.GN, SLEEPER_CLASS, 500.0, 0.0, 6 * 60L, false, 440.0, 60.0 },
			{ "RAC with meal 120 before the chart: meal back", Held.RAC, Quota.GN, SLEEPER_CLASS, 500.0, 120.0, 6 * 60L, false, 560.0, 60.0 },
			{ "RAC after the chart: clerkage, meal kept", Held.RAC, Quota.GN, SLEEPER_CLASS, 500.0, 120.0, 2 * 60L, true, 440.0, 180.0 },
			{ "Waitlisted: fare minus clerkage", Held.WAITLISTED, Quota.GN, AC3_TIER, 1000.0, 0.0, 24 * 60L, false, 940.0, 60.0 },
			{ "Waitlisted fare 40: clerkage can't be more than the fare", Held.WAITLISTED, Quota.GN, SECOND_CLASS, 40.0, 0.0, 24 * 60L, false, 0.0, 40.0 },
			{ "Tatkal waitlisted: clerkage only", Held.WAITLISTED, Quota.TQ, SLEEPER_CLASS, 650.0, 0.0, 6 * 60L, false, 590.0, 60.0 },
			{ "Tatkal confirmed: no refund of the fare, extras back", Held.CONFIRMED, Quota.TQ, SLEEPER_CLASS, 650.0, 120.0, 20 * 60L, false, 120.0, 650.0 },
			{ "Tatkal confirmed after the chart: nothing back", Held.CONFIRMED, Quota.TQ, SLEEPER_CLASS, 650.0, 120.0, 2 * 60L, true, 0.0, 770.0 },
			{ "Premium Tatkal confirmed: no refund of the fare", Held.CONFIRMED, Quota.PT, AC3_TIER, 1560.0, 0.0, 20 * 60L, false, 0.0, 1560.0 },
			{ "Premium Tatkal RAC: no refund at all", Held.RAC, Quota.PT, AC3_TIER, 1560.0, 120.0, 20 * 60L, false, 0.0, 1680.0 },
			{ "Fare with paise rounds to whole rupees", Held.CONFIRMED, Quota.GN, SLEEPER_CLASS, 500.4, 0.0, 50 * 60L, false, 380.0, 120.0 } };

	@Test
	void passengerCancels() {
		CaseTable t = CaseTable.of("Refunds", "Passenger cancels: refund and charge");
		for (Object[] c : CANCEL) {
			Held held = (Held) c[1];
			Quota quota = (Quota) c[2];
			Seat_type type = (Seat_type) c[3];
			double fare = (Double) c[4];
			double extras = (Double) c[5];
			long minutes = (Long) c[6];
			boolean chart = (Boolean) c[7];
			var inputs = in("held", held, "quota", quota, "class", CoachLayout.shortCode(type), "fare", fare, "extras", extras,
					"minutes before", minutes, "chart prepared", chart);
			t.check(c[0] + " (refund)", inputs, c[8], () -> RefundRules.onCancel(held, quota, type, fare, extras, minutes, chart).refund());
			t.check(c[0] + " (charge)", inputs, c[9], () -> RefundRules.onCancel(held, quota, type, fare, extras, minutes, chart).charge());
		}
		t.check("Refund + charge always equals what was paid", in("every case above", CANCEL.length + " cases"), true, () -> {
			for (Object[] c : CANCEL) {
				var r = RefundRules.onCancel((Held) c[1], (Quota) c[2], (Seat_type) c[3], (Double) c[4], (Double) c[5], (Long) c[6],
						(Boolean) c[7]);
				if (Math.abs(r.refund() + r.charge() - r.paid()) > 1) {
					return false;
				}
			}
			return true;
		});
		t.done();
	}

	@Test
	void rulesShownToThePassenger() {
		CaseTable t = CaseTable.of("Refunds", "The rule shown with each refund");
		t.check("More than 48 h", in("minutes before", 3000), "More than 48 hours before departure: flat charge",
				() -> RefundRules.onCancel(Held.CONFIRMED, Quota.GN, SLEEPER_CLASS, 500, 0, 3000, false).rule());
		t.check("48 to 12 h", in("minutes before", 1440), "48 to 12 hours before departure: 25% of the fare",
				() -> RefundRules.onCancel(Held.CONFIRMED, Quota.GN, SLEEPER_CLASS, 500, 0, 1440, false).rule());
		t.check("Under 12 h", in("minutes before", 300), "Less than 12 hours before departure: 50% of the fare",
				() -> RefundRules.onCancel(Held.CONFIRMED, Quota.GN, SLEEPER_CLASS, 500, 0, 300, false).rule());
		t.check("Tatkal", in("quota", "TQ"), "Tatkal confirmed: no refund of the fare",
				() -> RefundRules.onCancel(Held.CONFIRMED, Quota.TQ, SLEEPER_CLASS, 650, 0, 1440, false).rule());
		t.check("Waitlisted", in("held", "WAITLISTED"), "Waitlisted: clerkage charge",
				() -> RefundRules.onCancel(Held.WAITLISTED, Quota.GN, SLEEPER_CLASS, 500, 0, 1440, false).rule());
		t.check("RAC", in("held", "RAC"), "RAC: clerkage charge",
				() -> RefundRules.onCancel(Held.RAC, Quota.GN, SLEEPER_CLASS, 500, 0, 1440, false).rule());
		t.done();
	}

	@Test
	void railwayCancels() {
		CaseTable t = CaseTable.of("Refunds", "Dropped at the chart, or train cancelled by the railway");
		t.check("Still waitlisted at the chart: fare minus clerkage, extras back", in("fare", 500, "extras", 120), 560.0,
				() -> RefundRules.droppedAtChart(500, 120).refund());
		t.check("Still waitlisted at the chart: charge", in("fare", 500, "extras", 120), 60.0,
				() -> RefundRules.droppedAtChart(500, 120).charge());
		t.check("Waitlisted at the chart, fare 30", in("fare", 30, "extras", 0), 0.0, () -> RefundRules.droppedAtChart(30, 0).refund());
		t.check("Train cancelled: everything back", in("fare", 500, "extras", 120), 620.0,
				() -> RefundRules.trainCancelled(500, 120).refund());
		t.check("Train cancelled: no charge", in("fare", 500, "extras", 120), 0.0, () -> RefundRules.trainCancelled(500, 120).charge());
		t.done();
	}
}
