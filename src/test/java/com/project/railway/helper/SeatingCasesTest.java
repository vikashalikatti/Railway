package com.project.railway.helper;

import static com.project.railway.testsupport.CaseTable.in;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.project.railway.helper.CompanionRules.Candidate;
import com.project.railway.helper.CompanionRules.Neighbour;
import com.project.railway.helper.SeatPicker.Near;
import com.project.railway.helper.SeatPicker.Seat;
import com.project.railway.helper.SeatPicker.Traveller;
import com.project.railway.testsupport.CaseTable;

/** Which berth each passenger gets, coach choice, solo women, seniors and volunteer helpers */
class SeatingCasesTest {

	/** Free Sleeper berths of coach S1, numbers given */
	private static List<Seat> sleeper(String coach, int... numbers) {
		List<Seat> out = new ArrayList<>();
		for (int n : numbers) {
			out.add(new Seat(n, coach, n, CoachLayout.berthType(Seat_type.SLEEPER_CLASS, n), CoachLayout.bay(Seat_type.SLEEPER_CLASS, n)));
		}
		return out;
	}

	private static List<Integer> seatNumbers(Map<Integer, Seat> m) {
		List<Integer> out = new ArrayList<>();
		m.values().forEach(s -> out.add(s.seatNumber()));
		return out;
	}

	@Test
	void choosingBerths() {
		CaseTable t = CaseTable.of("Seating", "Who gets which berth");
		t.check("Senior gets a lower berth over an upper", in("passenger", "age 65", "free", "3 (UPPER), 4 (LOWER)"), List.of(4),
				() -> seatNumbers(SeatPicker.assign(List.of(new Traveller(0, 65, null)), sleeper("S1", 3, 4))));
		t.check("Asked for upper: gets the upper", in("passenger", "age 30, wants UPPER", "free", "1 (LOWER), 3 (UPPER)"), List.of(3),
				() -> seatNumbers(SeatPicker.assign(List.of(new Traveller(0, 30, "UPPER")), sleeper("S1", 1, 3))));
		t.check("Preference is not case-sensitive", in("passenger", "wants side_lower", "free", "1, 7 (SIDE_LOWER)"), List.of(7),
				() -> seatNumbers(SeatPicker.assign(List.of(new Traveller(0, 30, "side_lower")), sleeper("S1", 1, 7))));
		t.check("Family stays in one bay", in("passengers", "3 adults", "free", "1, 2, 3 (bay 0), 9, 10 (bay 1), 17 (bay 2)"), List.of(1, 2, 3),
				() -> seatNumbers(SeatPicker.assign(List.of(new Traveller(0, 30, null), new Traveller(1, 28, null), new Traveller(2, 5, null)),
						sleeper("S1", 1, 2, 3, 9, 10, 17))));
		t.check("Family stays in one coach", in("passengers", "adult wanting MIDDLE, adult", "free", "S1 seat 1, S2 seats 1 and 2"),
				List.of("S2", "S2"), () -> {
			List<Seat> seats = new ArrayList<>(sleeper("S1", 1));
			seats.addAll(List.of(new Seat(101, "S2", 1, "LOWER", 0, 0), new Seat(102, "S2", 2, "MIDDLE", 0, 0)));
			List<String> coaches = new ArrayList<>();
			// the first takes the middle berth in S2; the second then prefers S2 over the free berth in S1
			SeatPicker.assign(List.of(new Traveller(0, 30, "MIDDLE"), new Traveller(1, 30, null)), seats).values()
					.forEach(s -> coaches.add(s.coachNumber()));
			return coaches;
		});
		t.check("More passengers than seats: the extra one gets none", in("passengers", 3, "free seats", 2), 2,
				() -> SeatPicker.assign(List.of(new Traveller(0, 30, null), new Traveller(1, 30, null), new Traveller(2, 30, null)),
						sleeper("S1", 1, 2)).size());
		t.check("No free seats", in("passengers", 1, "free seats", 0), 0,
				() -> SeatPicker.assign(List.of(new Traveller(0, 30, null)), List.of()).size());
		t.check("Results come back in passenger order", in("passengers", "30 (no wish), 70 (senior)", "free", "1 (LOWER), 3 (UPPER)"),
				List.of(0, 1), () -> new ArrayList<>(SeatPicker.assign(List.of(new Traveller(0, 30, null), new Traveller(1, 70, null)),
						sleeper("S1", 1, 3)).keySet()));
		t.check("Senior still gets the lower when listed second", in("passengers", "30 (no wish), 70 (senior)", "free", "1 (LOWER), 3 (UPPER)"),
				1, () -> SeatPicker.assign(List.of(new Traveller(0, 30, null), new Traveller(1, 70, null)), sleeper("S1", 1, 3)).get(1).seatNumber());
		t.check("Solo woman goes to a bay with women", in("passenger", "woman 25 alone", "free", "1 (bay 0), 9 (bay 1, women there)"), List.of(9),
				() -> seatNumbers(SeatPicker.assign(List.of(new Traveller(0, 25, null, Near.WOMEN)),
						List.of(new Seat(1, "S1", 1, "LOWER", 0, 0), new Seat(9, "S1", 9, "LOWER", 1, Near.WOMEN)))));
		t.check("Score: senior on a lower berth", in("passenger", "age 65", "seat", "LOWER"), 8,
				() -> SeatPicker.score(new Traveller(0, 65, null), new Seat(1, "S1", 1, "LOWER", 0), null));
		t.check("Score: senior on an upper berth", in("passenger", "age 65", "seat", "UPPER"), -6,
				() -> SeatPicker.score(new Traveller(0, 65, null), new Seat(3, "S1", 3, "UPPER", 0), null));
		t.check("Score: wished berth, same bay as the group", in("passenger", "wants LOWER", "seat", "LOWER, same bay"), 20,
				() -> SeatPicker.score(new Traveller(0, 30, "LOWER"), new Seat(4, "S1", 4, "LOWER", 0), new Seat(1, "S1", 1, "LOWER", 0)));
		t.check("Score: same coach, two bays away", in("passenger", "no wish", "seat", "bay 2, group in bay 0"), 4,
				() -> SeatPicker.score(new Traveller(0, 30, null), new Seat(17, "S1", 17, "LOWER", 2), new Seat(1, "S1", 1, "LOWER", 0)));
		t.done();
	}

	@Test
	void choosingCoach() {
		CaseTable t = CaseTable.of("Seating", "Which coach a group goes to, and berth counts");
		t.check("Most free seats that fits", in("free", "S1=10, S2=30", "group", 4), "S2", () -> SeatPicker.pickCoach(map("S1", 10, "S2", 30), 4));
		t.check("Nobody fits: the emptiest coach", in("free", "S1=3, S2=2", "group", 4), "S1", () -> SeatPicker.pickCoach(map("S1", 3, "S2", 2), 4));
		t.check("Tie: the first coach", in("free", "S1=6, S2=6", "group", 2), "S1", () -> SeatPicker.pickCoach(map("S1", 6, "S2", 6), 2));
		t.check("No coaches", in("free", "none", "group", 1), null, () -> SeatPicker.pickCoach(map(), 1));
		t.check("Count berths", in("seats", "1, 2, 4, 7"), Map.of("LOWER", 2, "MIDDLE", 1, "SIDE_LOWER", 1),
				() -> SeatPicker.countByBerth(sleeper("S1", 1, 2, 4, 7)));
		t.check("Bay key", in("coach", "S1", "bay", 3), "S1#3", () -> SeatPicker.bayKey("S1", 3));
		t.done();
	}

	private static Map<String, Integer> map(Object... kv) {
		Map<String, Integer> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], (Integer) kv[i + 1]);
		}
		return m;
	}

	@Test
	void companions() {
		CaseTable t = CaseTable.of("Seating", "Solo women, seniors who ask for help, volunteer helpers");
		Object[][] female = { { "Female", true }, { "female ", true }, { "FEMALE", true }, { "Male", false }, { null, false } };
		for (Object[] c : female) {
			String g = (String) c[0];
			t.check("Gender " + (g == null ? "missing" : "\"" + g + "\"") + " is female?", in("gender", g), c[1], () -> CompanionRules.isFemale(g));
		}
		t.check("Senior alone can ask for help", in("age", 60, "passengers with berth", 1), true, () -> CompanionRules.canAskForAssistance(60, 1));
		t.check("Senior with family can't", in("age", 70, "passengers with berth", 2), false, () -> CompanionRules.canAskForAssistance(70, 2));
		t.check("Age 59 can't", in("age", 59, "passengers with berth", 1), false, () -> CompanionRules.canAskForAssistance(59, 1));
		int[] ages = { 17, 18, 59, 60 };
		boolean[] help = { false, true, true, false };
		for (int i = 0; i < ages.length; i++) {
			int a = ages[i];
			t.check("Age " + a + " can volunteer?", in("age", a), help[i], () -> CompanionRules.canHelp(a));
		}
		t.check("Woman 25 alone looks for women", in("age", 25, "gender", "Female", "alone", true), Near.WOMEN,
				() -> CompanionRules.seeks(25, "Female", false, false, 1));
		t.check("Woman 25 with family: nothing", in("age", 25, "gender", "Female", "passengers", 2), 0,
				() -> CompanionRules.seeks(25, "Female", false, false, 2));
		t.check("Girl 11 alone: not counted as a solo woman", in("age", 11, "gender", "Female", "alone", true), 0,
				() -> CompanionRules.seeks(11, "Female", false, false, 1));
		t.check("Woman 65 alone asking for help: women + helper", in("age", 65, "gender", "Female", "needs help", true), Near.WOMEN | Near.HELPER,
				() -> CompanionRules.seeks(65, "Female", true, false, 1));
		t.check("Volunteer: looks for a senior needing help", in("age", 30, "gender", "Male", "helper", true), Near.SENIOR_NEEDING_HELP,
				() -> CompanionRules.seeks(30, "Male", false, true, 1));
		t.check("Bays with women, helpers and seniors", in("booked", "woman 30 S1 bay 0; free helper S1 bay 1; matched senior S1 bay 2; girl 15 S1 bay 3"),
				Map.of("S1#0", Near.WOMEN, "S1#1", Near.HELPER), () -> CompanionRules.nearFlags(List.of(
						new Neighbour("S1", 0, "Female", 30, false, false, false), new Neighbour("S1", 1, "Male", 30, false, true, false),
						new Neighbour("S1", 2, "Male", 70, true, false, true), new Neighbour("S1", 3, "Female", 15, false, false, false))));
		t.check("First name", in("name", "Ravi Kumar"), "Ravi", () -> CompanionRules.firstName("Ravi Kumar"));
		t.check("First name of one word", in("name", " Meena "), "Meena", () -> CompanionRules.firstName(" Meena "));
		t.check("First name of nothing", in("name", null), "", () -> CompanionRules.firstName(null));
		t.done();
	}

	private static Candidate c(long id, long booking, String coach, int bay, int from, int to) {
		return new Candidate(id, booking, coach, bay, from, to);
	}

	private static List<String> pairs(List<CompanionRules.Match> m) {
		List<String> out = new ArrayList<>();
		m.forEach(x -> out.add(x.senior().id() + "->" + x.helper().id()));
		return out;
	}

	@Test
	void matchingHelpers() {
		CaseTable t = CaseTable.of("Seating", "Matching each senior with one volunteer helper");
		Candidate senior = c(1, 100, "S1", 2, 1, 4);
		t.check("Helper in the same coach, on board: matched", in("senior", "S1 bay 2, stops 1-4", "helper", "S1 bay 3, stops 0-5"), List.of("1->11"),
				() -> pairs(CompanionRules.match(List.of(senior), List.of(c(11, 200, "S1", 3, 0, 5)))));
		t.check("Helper in another coach: no match", in("senior", "S1", "helper", "S2"), List.of(),
				() -> pairs(CompanionRules.match(List.of(senior), List.of(c(11, 200, "S2", 2, 0, 5)))));
		t.check("Helper on the same ticket: no match", in("senior", "booking 100", "helper", "booking 100"), List.of(),
				() -> pairs(CompanionRules.match(List.of(senior), List.of(c(11, 100, "S1", 2, 0, 5)))));
		t.check("Helper boards later: no match", in("senior", "boards stop 1", "helper", "boards stop 2"), List.of(),
				() -> pairs(CompanionRules.match(List.of(senior), List.of(c(11, 200, "S1", 2, 2, 5)))));
		t.check("Helper gets off where the senior boards: no match", in("senior", "boards stop 1", "helper", "stops 0-1"), List.of(),
				() -> pairs(CompanionRules.match(List.of(senior), List.of(c(11, 200, "S1", 2, 0, 1)))));
		t.check("Helper who stays the whole way wins over a nearer one", in("helpers", "11: bay 2, gets off at 3; 12: bay 6, to 4"), List.of("1->12"),
				() -> pairs(CompanionRules.match(List.of(senior), List.of(c(11, 200, "S1", 2, 0, 3), c(12, 201, "S1", 6, 0, 4)))));
		t.check("Both stay: the nearer bay wins", in("helpers", "11: bay 6; 12: bay 3"), List.of("1->12"),
				() -> pairs(CompanionRules.match(List.of(senior), List.of(c(11, 200, "S1", 6, 0, 4), c(12, 201, "S1", 3, 0, 4)))));
		t.check("Same distance: the lower id wins", in("helpers", "12: bay 3; 11: bay 1"), List.of("1->11"),
				() -> pairs(CompanionRules.match(List.of(senior), List.of(c(12, 201, "S1", 3, 0, 4), c(11, 200, "S1", 1, 0, 4)))));
		t.check("One helper, two seniors: the first senior gets them", in("seniors", "ids 2 and 1", "helpers", 1), List.of("1->11"),
				() -> pairs(CompanionRules.match(List.of(c(2, 101, "S1", 2, 1, 4), senior), List.of(c(11, 200, "S1", 3, 0, 5)))));
		t.done();
	}
}
