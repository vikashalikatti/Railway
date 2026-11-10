package com.project.railway.helper;

import static com.project.railway.testsupport.CaseTable.in;
import static com.project.railway.helper.Seat_type.*;

import org.junit.jupiter.api.Test;

import com.project.railway.helper.TravelRules.Meal;
import com.project.railway.testsupport.CaseTable;

/** Children, infants, meals and bedrolls */
class TravelCasesTest {

	@Test
	void agesAndBerths() {
		CaseTable t = CaseTable.of("Travel rules", "Infants (under 5) and children (5-11)");
		int[] ages = { 0, 4, 5, 11, 12, 30, 65 };
		boolean[] infant = { true, true, false, false, false, false, false };
		boolean[] child = { false, false, true, true, false, false, false };
		for (int i = 0; i < ages.length; i++) {
			int age = ages[i];
			t.check("Age " + age + " is an infant?", in("age", age), infant[i], () -> TravelRules.isInfant(age));
			t.check("Age " + age + " is a child?", in("age", age), child[i], () -> TravelRules.isChild(age));
		}
		t.check("Infant always travels without a berth", in("age", 3, "no berth chosen", false), true,
				() -> TravelRules.travelsWithoutBerth(3, false));
		t.check("Child who chose no berth", in("age", 8, "no berth chosen", true), true, () -> TravelRules.travelsWithoutBerth(8, true));
		t.check("Child with a berth", in("age", 8, "no berth chosen", false), false, () -> TravelRules.travelsWithoutBerth(8, false));
		t.check("Adult can't choose no berth", in("age", 12, "no berth chosen", true), false,
				() -> TravelRules.travelsWithoutBerth(12, true));
		t.done();
	}

	@Test
	void ticketFares() {
		CaseTable t = CaseTable.of("Travel rules", "Ticket fare by age (before meals and bedroll)");
		Object[][] cases = { { "Infant travels free", 2, false, 500.0, 0.0 }, { "Infant, 'no berth' ticked: still free", 4, true, 500.0, 0.0 },
				{ "Child without berth: half fare", 7, true, 500.0, 250.0 }, { "Child without berth: half of 501 rounds to 251", 7, true, 501.0, 251.0 },
				{ "Child with berth: full fare", 7, false, 500.0, 500.0 }, { "Age 12, 'no berth' ticked: full fare", 12, true, 500.0, 500.0 },
				{ "Senior: full fare", 65, false, 500.0, 500.0 } };
		for (Object[] c : cases) {
			int age = (Integer) c[1];
			boolean noBerth = (Boolean) c[2];
			double fare = (Double) c[3];
			t.check((String) c[0], in("age", age, "no berth chosen", noBerth, "full fare", fare), c[4],
					() -> TravelRules.ticketFare(age, noBerth, fare));
		}
		t.done();
	}

	@Test
	void mealsAndBedrolls() {
		CaseTable t = CaseTable.of("Travel rules", "Meals, bedroll and their charges");
		for (Seat_type type : Seat_type.values()) {
			boolean included = type == AC1_TIER || type == AC2_TIER || type == AC3_TIER || type == AC3_ECONOMY;
			t.check(CoachLayout.shortCode(type) + " bedroll included?", in("class", CoachLayout.shortCode(type)), included,
					() -> TravelRules.bedrollIncluded(type));
			t.check(CoachLayout.shortCode(type) + " bedroll can be bought?", in("class", CoachLayout.shortCode(type)),
					type == SLEEPER_CLASS, () -> TravelRules.bedrollOffered(type));
		}
		t.check("SL: veg meal + bedroll", in("meal", "VEG", "bedroll", true, "class", "SL"), 270.0,
				() -> TravelRules.extras(Meal.VEG, true, SLEEPER_CLASS));
		t.check("3A: bedroll is included, not charged", in("meal", "VEG", "bedroll", true, "class", "3A"), 120.0,
				() -> TravelRules.extras(Meal.VEG, true, AC3_TIER));
		t.check("CC: no bedroll in a chair car", in("meal", "NON_VEG", "bedroll", true, "class", "CC"), 150.0,
				() -> TravelRules.extras(Meal.NON_VEG, true, AC_CHAIR_CAR));
		t.check("Jain meal", in("meal", "JAIN", "bedroll", false, "class", "SL"), 120.0, () -> TravelRules.extras(Meal.JAIN, false, SLEEPER_CLASS));
		t.check("Nothing extra", in("meal", "NONE", "bedroll", false, "class", "SL"), 0.0, () -> TravelRules.extras(Meal.NONE, false, SLEEPER_CLASS));
		t.check("Meal not given", in("text", null), Meal.NONE, () -> Meal.parse(null));
		t.check("Meal blank", in("text", "  "), Meal.NONE, () -> Meal.parse("  "));
		t.check("Meal 'non-veg'", in("text", "non-veg"), Meal.NON_VEG, () -> Meal.parse("non-veg"));
		t.check("Meal ' veg '", in("text", " veg "), Meal.VEG, () -> Meal.parse(" veg "));
		t.checkThrows("Unknown meal", in("text", "pizza"), IllegalArgumentException.class, null, () -> Meal.parse("pizza"));
		t.done();
	}
}
