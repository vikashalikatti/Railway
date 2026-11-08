package com.project.railway.dto;

import lombok.Data;

/** One passenger in a booking request (request body only, not stored as-is) */
@Data
public class PassengerRequest {
	private String name;
	private int age;
	/** Male, Female or Other */
	private String gender;
	/** LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER, WINDOW, MIDDLE_SEAT, AISLE, or empty for no preference */
	private String berthPreference;
	/** Children 5-11 only: travel without a berth at half fare. Under 5 never get a berth. */
	private boolean noBerth;
	/** NONE, VEG, NON_VEG or JAIN */
	private String meal;
	/** Sleeper only: add a bedroll (AC classes include one) */
	private boolean bedroll;
}
