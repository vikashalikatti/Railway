package com.project.railway.dto;

import java.util.List;

/** Responses for PNR status, connecting trains and running status (not stored) */
public final class TravelViews {

	private TravelViews() {
	}

	/** Public PNR status: like the railway's PNR enquiry, no names */
	public record PnrView(long pnr, int trainNumber, String trainName, String fromStation, String toStation,
			String journeyDate, String seatType, String quota, String chartStatus, String status, Double refundAmount,
			List<PnrPassenger> passengers) {
	}

	public record PnrPassenger(int no, String bookingStatus, String currentStatus) {
	}

	/** Two trains with a change at `via` */
	public record Connection(Object first, Object second, String via, long layoverMinutes) {
	}

	/** Where a train is on a date */
	public record Running(int trainNumber, String trainName, String journeyDate, boolean cancelled, String cancelReason,
			int delayMinutes, Integer currentStop, boolean departed, String note, String updatedAt, List<RunningStop> stops) {
	}

	/** expected times are the scheduled ones plus the delay; passed: the train has left this stop */
	public record RunningStop(String station, String code, String scheduledArrival, String scheduledDeparture,
			String expectedArrival, String expectedDeparture, boolean passed, boolean current) {
	}

	/** A train's timetable for the public timetable page */
	public record Timetable(int trainNumber, String trainName, String type, java.util.List<String> runningDays,
			java.util.List<String> classes, java.util.List<TimetableStop> stops, double totalKm, String duration) {
	}

	/** One stop: day 1, 2, 3…; km from the first station; halt in minutes */
	public record TimetableStop(int no, String station, String code, String arrives, String departs, int day, long halt,
			double km) {
	}

	/** A train found by number or name */
	public record TrainBrief(int trainNumber, String trainName, String from, String to) {
	}

	/** What a ticket checker sees after scanning an e-ticket's QR code */
	public record TicketCheck(boolean valid, String message, Long pnr, Integer trainNumber, String trainName,
			String from, String to, String boardingDate, String departs, String classLabel, String status,
			java.util.List<TicketCheckPassenger> passengers) {
	}

	public record TicketCheckPassenger(int no, String name, int age, String gender, String status) {
	}
}
