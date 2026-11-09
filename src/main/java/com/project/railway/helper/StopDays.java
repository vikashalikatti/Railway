package com.project.railway.helper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Day numbers of a train's stops (Day 1 = the day it leaves its first station). Plain Java so it
 * can be unit-tested.
 *
 * A stop's day is the day it ARRIVES. When the timetable gives no day, it is worked out from the
 * times: whenever a time is earlier than the one before it, midnight has passed.
 */
public final class StopDays {

	private StopDays() {
	}

	/** One stop: arrival and departure times, and the day if the timetable gives one (null / 0 = work it out) */
	public record Stop(LocalTime arrival, LocalTime departure, Integer day) {
	}

	/** Minutes after midnight of Day 1, for every stop's arrival and departure */
	public record Timeline(long[] arrival, long[] departure) {

		/** Day number (1, 2, 3…) the stop is reached */
		public int day(int stop) {
			return (int) (arrival[stop] / 1440) + 1;
		}

		/** Day number the train leaves this stop */
		public int departureDay(int stop) {
			return (int) (departure[stop] / 1440) + 1;
		}

		/** When the train leaves this stop, for a run that starts on runDate */
		public LocalDateTime departs(LocalDate runDate, int stop) {
			return runDate.atStartOfDay().plusMinutes(departure[stop]);
		}

		public LocalDateTime arrives(LocalDate runDate, int stop) {
			return runDate.atStartOfDay().plusMinutes(arrival[stop]);
		}

		/** The date the run started, for a passenger boarding at this stop on boardingDate */
		public LocalDate runDate(LocalDate boardingDate, int stop) {
			return boardingDate.minusDays(departureDay(stop) - 1L);
		}

		/** The date a passenger boards at this stop, for a run that started on runDate */
		public LocalDate boardingDate(LocalDate runDate, int stop) {
			return runDate.plusDays(departureDay(stop) - 1L);
		}

		/** Minutes from leaving one stop to reaching another */
		public long minutes(int fromStop, int toStop) {
			return arrival[toStop] - departure[fromStop];
		}
	}

	public static Timeline timeline(List<Stop> stops) {
		int n = stops.size();
		long[] arr = new long[n];
		long[] dep = new long[n];
		long last = -1; // minutes of the previous departure
		for (int i = 0; i < n; i++) {
			Stop s = stops.get(i);
			LocalTime a = s.arrival() != null ? s.arrival() : s.departure();
			LocalTime d = s.departure() != null ? s.departure() : a;
			if (a == null) {
				a = LocalTime.MIDNIGHT;
				d = LocalTime.MIDNIGHT;
			}
			long am;
			if (s.day() != null && s.day() > 0) {
				am = (s.day() - 1L) * 1440 + minutes(a);
				if (am < last) {
					am = next(last, a); // a day that goes backwards is a typing mistake: keep time moving forward
				}
			} else {
				am = last < 0 ? minutes(a) : next(last, a);
			}
			long dm = (am / 1440) * 1440 + minutes(d);
			if (dm < am) {
				dm += 1440; // leaves after midnight
			}
			arr[i] = am;
			dep[i] = dm;
			last = dm;
		}
		return new Timeline(arr, dep);
	}

	/** The first moment at or after `after` (minutes) whose time of day is t */
	private static long next(long after, LocalTime t) {
		long m = (after / 1440) * 1440 + minutes(t);
		return m < after ? m + 1440 : m;
	}

	private static long minutes(LocalTime t) {
		return t.getHour() * 60L + t.getMinute();
	}
}
