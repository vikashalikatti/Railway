package com.project.railway.helper;

import java.util.Locale;

/**
 * Children, infants, meals and bedrolls, modelled on Indian Railways' rules.
 * Plain Java so it can be unit-tested.
 *
 *  - Under 5: travels free, without a berth (an infant on the ticket); up to 2 per booking.
 *  - 5 to 11: full fare with a berth, or half fare without one ("no berth").
 *  - 12 and over: full fare, always with a berth.
 *  - Meals: none, veg, non-veg or Jain, paid per passenger.
 *  - Bedroll: included in AC sleeper classes; can be added in Sleeper; none in sitting classes.
 */
public final class TravelRules {

	private TravelRules() {
	}

	public static final int INFANT_UNDER = 5;
	public static final int CHILD_UNDER = 12;
	public static final int MAX_INFANTS = 2;
	public static final double BEDROLL_PRICE = 150;

	public enum Meal {
		NONE("No meal", 0), VEG("Veg", 120), NON_VEG("Non-veg", 150), JAIN("Jain", 120);

		public final String label;
		public final double price;

		Meal(String label, double price) {
			this.label = label;
			this.price = price;
		}

		public static Meal parse(String s) {
			if (s == null || s.isBlank()) {
				return NONE;
			}
			return Meal.valueOf(s.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
		}
	}

	public static boolean isInfant(int age) {
		return age < INFANT_UNDER;
	}

	public static boolean isChild(int age) {
		return age >= INFANT_UNDER && age < CHILD_UNDER;
	}

	/** Infants never get a berth; children 5-11 may choose not to; everyone else gets one */
	public static boolean travelsWithoutBerth(int age, boolean noBerthChosen) {
		return isInfant(age) || (isChild(age) && noBerthChosen);
	}

	/** Ticket fare for one passenger, before meals and bedroll */
	public static double ticketFare(int age, boolean noBerthChosen, double fullFare) {
		if (isInfant(age)) {
			return 0;
		}
		if (isChild(age) && noBerthChosen) {
			return Math.round(fullFare / 2);
		}
		return fullFare;
	}

	public static boolean bedrollIncluded(Seat_type type) {
		return type == Seat_type.AC1_TIER || type == Seat_type.AC2_TIER || type == Seat_type.AC3_TIER
				|| type == Seat_type.AC3_ECONOMY;
	}

	public static boolean bedrollOffered(Seat_type type) {
		return type == Seat_type.SLEEPER_CLASS;
	}

	/** Meal and bedroll charges for one passenger */
	public static double extras(Meal meal, boolean bedroll, Seat_type type) {
		return meal.price + (bedroll && bedrollOffered(type) ? BEDROLL_PRICE : 0);
	}
}
