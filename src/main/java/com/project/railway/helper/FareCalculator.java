package com.project.railway.helper;

/**
 * Fare for one passenger, based on the distance between the passenger's own
 * boarding and destination stops:
 *
 *   fare = base fare (set by admin on the route) + rate per km x distance + reservation charge
 *
 * Plain Java so it can be unit-tested.
 */
public final class FareCalculator {

	private FareCalculator() {
	}

	/** Shortest distance charged for, as railways do for very short trips */
	public static final double MIN_CHARGED_KM = 50;

	public static double ratePerKm(Seat_type type) {
		switch (type) {
		case SECOND_CLASS:
			return 0.45;
		case SLEEPER_CLASS:
			return 0.70;
		case AC3_TIER:
			return 1.80;
		case AC2_TIER:
			return 2.60;
		case AC1_TIER:
			return 4.40;
		case AC_CHAIR_CAR:
			return 1.40;
		case AC3_ECONOMY:
			return 1.65;
		case EXECUTIVE_CHAIR_CAR:
			return 2.80;
		default:
			throw new IllegalArgumentException("Unknown seat type " + type);
		}
	}

	public static double reservationCharge(Seat_type type) {
		switch (type) {
		case SECOND_CLASS:
			return 15;
		case SLEEPER_CLASS:
			return 20;
		case AC3_TIER:
			return 40;
		case AC2_TIER:
			return 50;
		case AC1_TIER:
		case EXECUTIVE_CHAIR_CAR:
			return 60;
		case AC_CHAIR_CAR:
		case AC3_ECONOMY:
			return 40;
		default:
			throw new IllegalArgumentException("Unknown seat type " + type);
		}
	}

	/** Fare per passenger, rounded to the nearest rupee */
	public static double perPassenger(Seat_type type, double distanceKm, double baseFare) {
		double km = Math.max(distanceKm, MIN_CHARGED_KM);
		double fare = Math.max(baseFare, 0) + ratePerKm(type) * km + reservationCharge(type);
		return Math.round(fare);
	}
}
