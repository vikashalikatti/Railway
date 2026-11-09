package com.project.railway.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Seats left in one class for one journey (response only, not stored) */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Availability {
	private String seatType;
	/** Free seats for the passenger's segment */
	private long available;
	/** People already waiting in this class on this date */
	private long waitlisted;
	/** Fare per passenger for this segment */
	private double fare;
	/** AVAILABLE-0048, WL 3, TQWL 3, REGRET (waitlist full), NOT AVAILABLE or CHART PREPARED */
	private String status;
	/** Berth preferences passengers can choose in this class */
	private List<String> berthPreferences;
	/** GN, TQ, PT, LD or SS */
	private String quota;
	/** Why this class can't be booked right now (Tatkal not open yet, chart prepared…), or null */
	private String note;
	/** True once the first chart is prepared for this train and date */
	private boolean chartPrepared;
}
