package com.project.railway.dto;

import java.util.List;

/**
 * Reservation chart of one train run (response only, not stored).
 *
 * Passengers see every berth and the stretches it is booked for, so they can find vacant ones,
 * but never who is on it: passengers is null for them. The admin also gets the passenger list.
 */
public record ChartView(int trainNumber, String trainName, String date, int stage, String preparedAt,
		String chartAt, List<String> stops, List<ChartClass> classes, List<ChartPassenger> passengers) {

	/** One class: its coaches in order */
	public record ChartClass(String seatType, int berths, int vacantWholeRoute, List<ChartCoach> coaches) {
	}

	public record ChartCoach(String coachNumber, List<ChartBerth> berths) {
	}

	/** booked: stretches of the route the berth is taken for, as [fromStop, toStop) stop positions */
	public record ChartBerth(int seatNumber, String berthType, String quota, List<Segment> booked) {
	}

	public record Segment(int from, int to) {
	}

	/** Admin only */
	public record ChartPassenger(long pnr, String name, int age, String gender, String seatType, String coach,
			Integer seat, String berth, String from, String to, String quota, String status) {
	}
}
