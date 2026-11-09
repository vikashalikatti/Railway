package com.project.railway.service;

import java.time.LocalDate;

import com.project.railway.helper.Quota;
import com.project.railway.helper.Seat_type;

/**
 * A validated journey: which train, class, date and stretch of the route.
 * fromStop / toStop are positions on the route (0 = first station).
 *
 * quota          the quota seats are sold in
 * anyQuota       true after the first chart: seats of every quota are open
 * waitlist       whether passengers without a seat may join the waitlist
 * closedReason   why booking isn't possible right now (Tatkal not open yet, chart prepared…), or null
 */
public record JourneyPlan(int trainNumber, String trainName, Seat_type seatType, LocalDate date, int fromStop,
		int toStop, String fromStation, String toStation, double distanceKm, double farePerPassenger, Quota quota,
		boolean anyQuota, boolean waitlist, String closedReason, LocalDate boardingDate) {

	/** date is the day the train leaves its first station; boardingDate the day the passenger gets on */
	public JourneyPlan(int trainNumber, String trainName, Seat_type seatType, LocalDate date, int fromStop, int toStop,
			String fromStation, String toStation, double distanceKm, double farePerPassenger, Quota quota,
			boolean anyQuota, boolean waitlist, String closedReason) {
		this(trainNumber, trainName, seatType, date, fromStop, toStop, fromStation, toStation, distanceKm,
				farePerPassenger, quota, anyQuota, waitlist, closedReason, date);
	}

	/** The same journey, selling seats of another quota */
	public JourneyPlan withQuota(Quota q, boolean any) {
		return new JourneyPlan(trainNumber, trainName, seatType, date, fromStop, toStop, fromStation, toStation,
				distanceKm, farePerPassenger, q, any, waitlist, closedReason, boardingDate);
	}
}
