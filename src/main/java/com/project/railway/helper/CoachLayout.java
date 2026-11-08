package com.project.railway.helper;

import java.util.List;

/**
 * Physical layout of each coach type: how many seats it has, the coach-number prefix,
 * and which berth/seat type every seat number is.
 *
 * Plain Java (no Spring) so it can be unit-tested on its own.
 */
public final class CoachLayout {

	private CoachLayout() {
	}

	public static final String LOWER = "LOWER";
	public static final String MIDDLE = "MIDDLE";
	public static final String UPPER = "UPPER";
	public static final String SIDE_LOWER = "SIDE_LOWER";
	public static final String SIDE_MIDDLE = "SIDE_MIDDLE";
	public static final String SIDE_UPPER = "SIDE_UPPER";
	public static final String WINDOW = "WINDOW";
	public static final String MIDDLE_SEAT = "MIDDLE_SEAT";
	public static final String AISLE = "AISLE";

	/** Most coaches a single train may have */
	public static final int MAX_COACHES = 24;

	/** Seats per coach */
	public static int capacity(Seat_type type) {
		switch (type) {
		case SECOND_CLASS:
			return 108;
		case SLEEPER_CLASS:
			return 72;
		case AC3_TIER:
			return 72;
		case AC2_TIER:
			return 48;
		case AC1_TIER:
			return 18;
		case AC_CHAIR_CAR:
			return 78;
		case EXECUTIVE_CHAIR_CAR:
			return 56;
		case AC3_ECONOMY:
			return 81;
		default:
			throw new IllegalArgumentException("Unknown seat type " + type);
		}
	}

	/** Coach number prefix, as printed on Indian Railways coaches: D1, S1, B1, A1, H1, C1, E1, M1 */
	public static String prefix(Seat_type type) {
		switch (type) {
		case SECOND_CLASS:
			return "D";
		case SLEEPER_CLASS:
			return "S";
		case AC3_TIER:
			return "B";
		case AC2_TIER:
			return "A";
		case AC1_TIER:
			return "H";
		case AC_CHAIR_CAR:
			return "C";
		case EXECUTIVE_CHAIR_CAR:
			return "E";
		case AC3_ECONOMY:
			return "M";
		default:
			throw new IllegalArgumentException("Unknown seat type " + type);
		}
	}

	/**
	 * Berth type of a seat number.
	 * SL / 3A: bays of 8 -> L M U L M U SL SU
	 * 2A:      bays of 6 -> L U L U SL SU
	 * 1A:      cabins    -> L U L U ...
	 * 3E:      bays of 9 -> L M U L M U SL SM SU
	 * 2S:      rows of 6 -> W M A A M W
	 * CC:      rows of 5 -> W M A | A W   (3+2)
	 * EC:      rows of 4 -> W A | A W     (2+2)
	 */
	public static String berthType(Seat_type type, int seatNumber) {
		switch (type) {
		case SLEEPER_CLASS:
		case AC3_TIER: {
			String[] bay = { LOWER, MIDDLE, UPPER, LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER };
			return bay[(seatNumber - 1) % 8];
		}
		case AC2_TIER: {
			String[] bay = { LOWER, UPPER, LOWER, UPPER, SIDE_LOWER, SIDE_UPPER };
			return bay[(seatNumber - 1) % 6];
		}
		case AC1_TIER:
			return seatNumber % 2 == 1 ? LOWER : UPPER;
		case SECOND_CLASS: {
			String[] row = { WINDOW, MIDDLE_SEAT, AISLE, AISLE, MIDDLE_SEAT, WINDOW };
			return row[(seatNumber - 1) % 6];
		}
		case AC3_ECONOMY: {
			String[] bay = { LOWER, MIDDLE, UPPER, LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_MIDDLE, SIDE_UPPER };
			return bay[(seatNumber - 1) % 9];
		}
		case AC_CHAIR_CAR: {
			String[] row = { WINDOW, MIDDLE_SEAT, AISLE, AISLE, WINDOW };
			return row[(seatNumber - 1) % 5];
		}
		case EXECUTIVE_CHAIR_CAR: {
			String[] row = { WINDOW, AISLE, AISLE, WINDOW };
			return row[(seatNumber - 1) % 4];
		}
		default:
			throw new IllegalArgumentException("Unknown seat type " + type);
		}
	}

	/** Which bay / cabin / row a seat is in. Passengers in the same bay sit together. */
	public static int bay(Seat_type type, int seatNumber) {
		switch (type) {
		case SLEEPER_CLASS:
		case AC3_TIER:
			return (seatNumber - 1) / 8;
		case AC2_TIER:
		case SECOND_CLASS:
			return (seatNumber - 1) / 6;
		case AC1_TIER:
		case EXECUTIVE_CHAIR_CAR:
			return (seatNumber - 1) / 4;
		case AC_CHAIR_CAR:
			return (seatNumber - 1) / 5;
		case AC3_ECONOMY:
			return (seatNumber - 1) / 9;
		default:
			throw new IllegalArgumentException("Unknown seat type " + type);
		}
	}

	/** The berth preferences a passenger can choose for this class */
	public static List<String> preferences(Seat_type type) {
		switch (type) {
		case SLEEPER_CLASS:
		case AC3_TIER:
			return List.of(LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER);
		case AC2_TIER:
			return List.of(LOWER, UPPER, SIDE_LOWER, SIDE_UPPER);
		case AC1_TIER:
			return List.of(LOWER, UPPER);
		case SECOND_CLASS:
		case AC_CHAIR_CAR:
			return List.of(WINDOW, MIDDLE_SEAT, AISLE);
		case EXECUTIVE_CHAIR_CAR:
			return List.of(WINDOW, AISLE);
		case AC3_ECONOMY:
			return List.of(LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_MIDDLE, SIDE_UPPER);
		default:
			return List.of();
		}
	}

	public static boolean isLower(String berth) {
		return LOWER.equals(berth) || SIDE_LOWER.equals(berth);
	}

	public static boolean isUpper(String berth) {
		return UPPER.equals(berth) || SIDE_UPPER.equals(berth);
	}
}
