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
}
