package com.project.railway.helper;

/**
 * Reserved classes a train can carry. New classes go at the end so stored values keep working.
 * Unreserved (General) coaches are not here: they have no seat numbers and aren't booked online.
 */
public enum Seat_type {
	SECOND_CLASS, // 2S  Second sitting
	SLEEPER_CLASS, // SL  Sleeper
	AC3_TIER, // 3A  AC 3 tier
	AC2_TIER, // 2A  AC 2 tier
	AC1_TIER, // 1A  AC first class
	AC_CHAIR_CAR, // CC  AC chair car, 3+2 seats
	EXECUTIVE_CHAIR_CAR, // EC  Executive chair car, 2+2 seats
	AC3_ECONOMY // 3E  AC 3 economy, 3 side berths
}
