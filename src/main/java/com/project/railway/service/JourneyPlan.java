package com.project.railway.service;

import java.time.LocalDate;

import com.project.railway.helper.Seat_type;

/**
 * A validated journey: which train, class, date and stretch of the route.
 * fromStop / toStop are positions on the route (0 = first station).
 */
public record JourneyPlan(int trainNumber, String trainName, Seat_type seatType, LocalDate date, int fromStop,
		int toStop, String fromStation, String toStation, double distanceKm, double farePerPassenger) {
}
