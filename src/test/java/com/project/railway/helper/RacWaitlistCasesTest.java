package com.project.railway.helper;

import static com.project.railway.testsupport.CaseTable.in;
import static com.project.railway.helper.Seat_type.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.project.railway.helper.RacRules.Berth;
import com.project.railway.helper.RacRules.Stretch;
import com.project.railway.testsupport.CaseTable;

/** RAC berths, RAC places, placing RAC passengers in pairs, and the waitlist confirmation chance */
class RacWaitlistCasesTest {

	@Test
	void racPerClass() {
		CaseTable t = CaseTable.of("RAC and waitlist", "Which classes have RAC, and how many RAC berths per coach");
		Object[][] cases = { { SLEEPER_CLASS, true, 4, 9 }, { AC3_TIER, true, 2, 9 }, { AC3_ECONOMY, true, 2, 9 }, { AC2_TIER, true, 1, 8 },
				{ AC1_TIER, false, 0, 0 }, { AC_CHAIR_CAR, false, 0, 0 }, { EXECUTIVE_CHAIR_CAR, false, 0, 0 }, { SECOND_CLASS, false, 0, 0 } };
		for (Object[] c : cases) {
			Seat_type type = (Seat_type) c[0];
			String cls = CoachLayout.shortCode(type);
			t.check(cls + " has RAC?", in("class", cls), c[1], () -> RacRules.hasRac(type));
			t.check(cls + " default RAC berths per coach", in("class", cls), c[2], () -> RacRules.defaultBerthsPerCoach(type));
			t.check(cls + " most RAC berths per coach (its side-lowers)", in("class", cls), c[3], () -> RacRules.maxBerthsPerCoach(type));
		}
		t.done();
	}

	@Test
	void racRoom() {
		CaseTable t = CaseTable.of("RAC and waitlist", "RAC places left on a stretch of the route");
		t.check("Nobody on RAC yet", in("places", 4, "booked", "none", "stretch", "0-3"), 4, () -> RacRules.room(4, List.of(), 0, 3));
		t.check("Two on the whole stretch", in("places", 4, "booked", "0-2, 0-2", "stretch", "1-3"), 2,
				() -> RacRules.room(4, List.of(new Stretch(0, 2), new Stretch(0, 2)), 1, 3));
		t.check("One gets off where the other gets on: they share the place", in("places", 4, "booked", "0-1, 1-2", "stretch", "0-2"), 3,
				() -> RacRules.room(4, List.of(new Stretch(0, 1), new Stretch(1, 2)), 0, 2));
		t.check("Booked stretch doesn't overlap", in("places", 4, "booked", "2-3", "stretch", "0-2"), 4,
				() -> RacRules.room(4, List.of(new Stretch(2, 3)), 0, 2));
		t.check("More booked than places: 0, never negative", in("places", 2, "booked", "3 x 0-4", "stretch", "0-4"), 0,
				() -> RacRules.room(2, List.of(new Stretch(0, 4), new Stretch(0, 4), new Stretch(0, 4)), 0, 4));
		t.check("Class without RAC places", in("places", 0, "booked", "none", "stretch", "0-1"), 0, () -> RacRules.room(0, List.of(), 0, 1));
		t.done();
	}

	private static List<Berth> berths(int n) {
		List<Berth> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(new Berth(100 + i));
		}
		return out;
	}

	@Test
	void placingRacPassengers() {
		CaseTable t = CaseTable.of("RAC and waitlist", "Placing RAC passengers on RAC berths at the chart (two per berth)");
		Stretch all = new Stretch(0, 4);
		t.check("3 passengers, 2 berths: the first berth is filled first", in("passengers", "3 x 0-4", "berths", 2), new int[] { 0, 0, 1 },
				() -> RacRules.place(List.of(all, all, all), berths(2)));
		t.check("4 passengers, 1 berth: 2 left without a berth (-1)", in("passengers", "4 x 0-4", "berths", 1), new int[] { 0, 0, -1, -1 },
				() -> RacRules.place(List.of(all, all, all, all), berths(1)));
		t.check("Back-to-back stretches share with a third", in("passengers", "0-2, 2-4, 0-4", "berths", 1), new int[] { 0, 0, 0 },
				() -> RacRules.place(List.of(new Stretch(0, 2), new Stretch(2, 4), all), berths(1)));
		t.check("Berth booked by someone for part of the route is skipped", in("passengers", "0-4", "berths", "2, first blocked 1-3"),
				new int[] { 1 }, () -> {
					List<Berth> b = berths(2);
					b.get(0).blocked.add(new Stretch(1, 3));
					return RacRules.place(List.of(all), b);
				});
		t.check("No berths at all", in("passengers", "0-4", "berths", 0), new int[] { -1 }, () -> RacRules.place(List.of(all), berths(0)));
		t.done();
	}

	@Test
	void waitlistChance() {
		CaseTable t = CaseTable.of("RAC and waitlist", "Chance that a waitlisted ticket is confirmed (percent and label)");
		Object[][] cases = {
				// name, position, class seats, RAC places, history, confirmed, percent, label
				{ "History: 15 of 20 confirmed", 5, 720L, 0L, 20, 15, 75, "High" },
				{ "History: 10 of 20", 5, 720L, 0L, 20, 10, 50, "Medium" },
				{ "History: 2 of 20", 5, 720L, 0L, 20, 2, 10, "Low" },
				{ "History exactly at High (70%)", 9, 720L, 0L, 100, 70, 70, "High" },
				{ "Just under High (69%)", 9, 720L, 0L, 100, 69, 69, "Medium" },
				{ "Exactly at Medium (35%)", 9, 720L, 0L, 20, 7, 35, "Medium" },
				{ "Just under Medium (34%)", 9, 720L, 0L, 100, 34, 34, "Low" },
				{ "Too little history (19): rule of thumb, WL 1", 1, 720L, 0L, 19, 0, 95, "High" },
				{ "Rule of thumb: WL 30 of 720 berths", 30, 720L, 0L, 0, 0, 50, "Medium" },
				{ "Rule of thumb: WL 60, never below 3%", 60, 720L, 0L, 0, 0, 3, "Low" },
				{ "RAC places help: WL 5, 72 berths, 10 RAC places", 5, 72L, 10L, 0, 0, 75, "High" },
				{ "No berths known, WL 2", 2, 0L, 0L, 0, 0, 3, "Low" } };
		for (Object[] c : cases) {
			int pos = (Integer) c[1];
			long seats = (Long) c[2];
			long rac = (Long) c[3];
			int hist = (Integer) c[4];
			int conf = (Integer) c[5];
			var inputs = in("WL position", pos, "berths", seats, "RAC places", rac, "history", hist, "confirmed", conf);
			t.check(c[0] + " (percent)", inputs, c[6], () -> WaitlistChance.estimate(pos, seats, rac, hist, conf).percent());
			t.check(c[0] + " (label)", inputs, c[7], () -> WaitlistChance.estimate(pos, seats, rac, hist, conf).label());
		}
		t.done();
	}
}
