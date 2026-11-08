package com.project.railway.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TravelRulesTest {

	@Test
	void infantsTravelFreeWithoutABerth() {
		assertTrue(TravelRules.travelsWithoutBerth(3, false));
		assertEquals(0.0, TravelRules.ticketFare(4, false, 600));
	}

	@Test
	void childrenChooseBerthOrHalfFare() {
		assertFalse(TravelRules.travelsWithoutBerth(8, false));
		assertEquals(600.0, TravelRules.ticketFare(8, false, 600));
		assertTrue(TravelRules.travelsWithoutBerth(8, true));
		assertEquals(300.0, TravelRules.ticketFare(8, true, 600));
	}

	@Test
	void adultsAlwaysGetABerthAndFullFare() {
		assertFalse(TravelRules.travelsWithoutBerth(12, true));
		assertEquals(600.0, TravelRules.ticketFare(30, true, 600));
	}

	@Test
	void mealsAndBedroll() {
		assertEquals(150.0, TravelRules.extras(TravelRules.Meal.NON_VEG, false, Seat_type.SLEEPER_CLASS));
		assertEquals(270.0, TravelRules.extras(TravelRules.Meal.VEG, true, Seat_type.SLEEPER_CLASS));
		assertEquals(120.0, TravelRules.extras(TravelRules.Meal.VEG, true, Seat_type.AC3_TIER)); // bedroll included
		assertTrue(TravelRules.bedrollIncluded(Seat_type.AC2_TIER));
		assertFalse(TravelRules.bedrollOffered(Seat_type.AC_CHAIR_CAR));
		assertEquals(TravelRules.Meal.NON_VEG, TravelRules.Meal.parse("non-veg"));
		assertEquals(TravelRules.Meal.NONE, TravelRules.Meal.parse(""));
	}
}
