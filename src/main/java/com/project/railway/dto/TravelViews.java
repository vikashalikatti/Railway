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
}
