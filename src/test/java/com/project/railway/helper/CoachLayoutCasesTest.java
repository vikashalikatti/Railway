package com.project.railway.helper;

import static com.project.railway.testsupport.CaseTable.in;
import static com.project.railway.helper.Seat_type.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.project.railway.testsupport.CaseTable;

/** Coach layouts: seats per coach, coach letters, class codes, berth types and bays */
class CoachLayoutCasesTest {

	@Test
	void coachesPerClass() {
		CaseTable t = CaseTable.of("Coach layouts", "Seats per coach, coach letter and class code");
		Object[][] cases = { { SECOND_CLASS, 108, "D", "2S", false }, { SLEEPER_CLASS, 72, "S", "SL", true },
				{ AC3_TIER, 72, "B", "3A", true }, { AC2_TIER, 48, "A", "2A", true }, { AC1_TIER, 18, "H", "1A", true },
				{ AC_CHAIR_CAR, 78, "C", "CC", false }, { EXECUTIVE_CHAIR_CAR, 56, "E", "EC", false },
				{ AC3_ECONOMY, 81, "M", "3E", true } };
		for (Object[] c : cases) {
			Seat_type type = (Seat_type) c[0];
			t.check(type + " seats", in("class", type), c[1], () -> CoachLayout.capacity(type));
			t.check(type + " coach letter", in("class", type), c[2], () -> CoachLayout.prefix(type));
			t.check(type + " class code", in("class", type), c[3], () -> CoachLayout.shortCode(type));
			t.check(type + " has berths?", in("class", type), c[4], () -> CoachLayout.hasBerths(type));
		}
		t.done();
	}

	@Test
	void berthTypes() {
		CaseTable t = CaseTable.of("Coach layouts", "Berth or seat type of a seat number");
		Object[][] cases = { { SLEEPER_CLASS, 1, "LOWER" }, { SLEEPER_CLASS, 2, "MIDDLE" }, { SLEEPER_CLASS, 3, "UPPER" },
				{ SLEEPER_CLASS, 4, "LOWER" }, { SLEEPER_CLASS, 7, "SIDE_LOWER" }, { SLEEPER_CLASS, 8, "SIDE_UPPER" },
				{ SLEEPER_CLASS, 9, "LOWER" }, { SLEEPER_CLASS, 72, "SIDE_UPPER" }, { AC3_TIER, 15, "SIDE_LOWER" },
				{ AC2_TIER, 1, "LOWER" }, { AC2_TIER, 2, "UPPER" }, { AC2_TIER, 5, "SIDE_LOWER" }, { AC2_TIER, 6, "SIDE_UPPER" },
				{ AC2_TIER, 7, "LOWER" }, { AC1_TIER, 1, "LOWER" }, { AC1_TIER, 2, "UPPER" }, { AC1_TIER, 17, "LOWER" },
				{ AC3_ECONOMY, 7, "SIDE_LOWER" }, { AC3_ECONOMY, 8, "SIDE_MIDDLE" }, { AC3_ECONOMY, 9, "SIDE_UPPER" },
				{ AC3_ECONOMY, 10, "LOWER" }, { SECOND_CLASS, 1, "WINDOW" }, { SECOND_CLASS, 2, "MIDDLE_SEAT" },
				{ SECOND_CLASS, 3, "AISLE" }, { SECOND_CLASS, 4, "AISLE" }, { SECOND_CLASS, 6, "WINDOW" },
				{ AC_CHAIR_CAR, 1, "WINDOW" }, { AC_CHAIR_CAR, 2, "MIDDLE_SEAT" }, { AC_CHAIR_CAR, 3, "AISLE" },
				{ AC_CHAIR_CAR, 4, "AISLE" }, { AC_CHAIR_CAR, 5, "WINDOW" }, { AC_CHAIR_CAR, 6, "WINDOW" },
				{ EXECUTIVE_CHAIR_CAR, 1, "WINDOW" }, { EXECUTIVE_CHAIR_CAR, 2, "AISLE" }, { EXECUTIVE_CHAIR_CAR, 4, "WINDOW" } };
		for (Object[] c : cases) {
			Seat_type type = (Seat_type) c[0];
			int seat = (Integer) c[1];
			t.check(CoachLayout.shortCode(type) + " seat " + seat, in("class", CoachLayout.shortCode(type), "seat", seat), c[2],
					() -> CoachLayout.berthType(type, seat));
		}
		t.done();
	}

	@Test
	void baysAndPreferences() {
		CaseTable t = CaseTable.of("Coach layouts", "Bays (who sits together) and berth preferences");
		Object[][] bays = { { SLEEPER_CLASS, 1, 0 }, { SLEEPER_CLASS, 8, 0 }, { SLEEPER_CLASS, 9, 1 }, { SLEEPER_CLASS, 72, 8 },
				{ AC2_TIER, 7, 1 }, { AC1_TIER, 5, 1 }, { AC_CHAIR_CAR, 6, 1 }, { SECOND_CLASS, 108, 17 }, { AC3_ECONOMY, 81, 8 } };
		for (Object[] c : bays) {
			Seat_type type = (Seat_type) c[0];
			int seat = (Integer) c[1];
			t.check(CoachLayout.shortCode(type) + " seat " + seat + " bay", in("class", CoachLayout.shortCode(type), "seat", seat), c[2],
					() -> CoachLayout.bay(type, seat));
		}
		t.check("SL preferences", in("class", "SL"), List.of("LOWER", "MIDDLE", "UPPER", "SIDE_LOWER", "SIDE_UPPER"),
				() -> CoachLayout.preferences(SLEEPER_CLASS));
		t.check("1A preferences", in("class", "1A"), List.of("LOWER", "UPPER"), () -> CoachLayout.preferences(AC1_TIER));
		t.check("EC preferences", in("class", "EC"), List.of("WINDOW", "AISLE"), () -> CoachLayout.preferences(EXECUTIVE_CHAIR_CAR));
		t.check("Side lower is a lower berth", in("berth", "SIDE_LOWER"), true, () -> CoachLayout.isLower("SIDE_LOWER"));
		t.check("Middle is not lower", in("berth", "MIDDLE"), false, () -> CoachLayout.isLower("MIDDLE"));
		t.check("Side upper is an upper berth", in("berth", "SIDE_UPPER"), true, () -> CoachLayout.isUpper("SIDE_UPPER"));
		t.check("Window is not upper", in("berth", "WINDOW"), false, () -> CoachLayout.isUpper("WINDOW"));
		t.check("Every seat number of every class has a type", in("classes", "all 8", "seats", "1 to capacity"), true, () -> {
			for (Seat_type type : Seat_type.values()) {
				for (int s = 1; s <= CoachLayout.capacity(type); s++) {
					if (CoachLayout.berthType(type, s) == null) {
						return false;
					}
				}
			}
			return true;
		});
		t.done();
	}
}
