package com.project.railway.helper;

import static com.project.railway.testsupport.CaseTable.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.project.railway.helper.StopDays.Stop;
import com.project.railway.helper.StopDays.Timeline;
import com.project.railway.testsupport.CaseTable;

/** Day numbers of stops, overnight journeys, run date vs boarding date; per-client rate limits */
class TimetableCasesTest {

	/** "arr-dep" pairs, e.g. "-22:00", "02:00-02:10", "06:00-" */
	private static Timeline line(String... stops) {
		List<Stop> out = new ArrayList<>();
		for (String s : stops) {
			String[] p = s.split("-", -1);
			out.add(new Stop(p[0].isEmpty() ? null : LocalTime.parse(p[0]), p[1].isEmpty() ? null : LocalTime.parse(p[1]), null));
		}
		return StopDays.timeline(out);
	}

	@Test
	void dayNumbers() {
		CaseTable t = CaseTable.of("Timetable", "Day number of each stop, worked out from the times");
		t.check("Same-day train", in("stops", "-06:00, 08:00-08:05, 12:00-"), List.of(1, 1, 1),
				() -> days(line("-06:00", "08:00-08:05", "12:00-")));
		t.check("Overnight: arrives after midnight on Day 2", in("stops", "-22:00, 02:00-02:10, 06:00-"), List.of(1, 2, 2),
				() -> days(line("-22:00", "02:00-02:10", "06:00-")));
		t.check("Two nights: Day 3", in("stops", "-20:00, 23:00-23:10, 10:00-10:10, 21:00-21:05, 05:00-"), List.of(1, 1, 2, 2, 3),
				() -> days(line("-20:00", "23:00-23:10", "10:00-10:10", "21:00-21:05", "05:00-")));
		t.check("Arrives 23:55, leaves 00:05: arrival Day 1, departure Day 2",
				in("stops", "-20:00, 23:55-00:05, 03:00-"), List.of(1, 2), () -> {
					Timeline tl = line("-20:00", "23:55-00:05", "03:00-");
					return List.of(tl.day(1), tl.departureDay(1));
				});
		t.check("Same time at the next stop means the same moment, not a day later", in("stops", "-10:00, 10:00-10:00"), List.of(1, 1),
				() -> days(line("-10:00", "10:00-10:00")));
		t.check("Day numbers given by the timetable are used", in("stops", "-08:00 (day 1), 09:00 (day 2)"), List.of(1, 2),
				() -> days(StopDays.timeline(List.of(new Stop(null, LocalTime.of(8, 0), 1), new Stop(LocalTime.of(9, 0), null, 2)))));
		t.check("A day that goes backwards is corrected", in("stops", "-22:00 (day 2), 06:00 (day 1)"), List.of(2, 3),
				() -> days(StopDays.timeline(List.of(new Stop(null, LocalTime.of(22, 0), 2), new Stop(LocalTime.of(6, 0), null, 1)))));
		t.check("Day 0 means 'work it out'", in("stops", "-22:00, 02:00 (day 0)"), List.of(1, 2),
				() -> days(StopDays.timeline(List.of(new Stop(null, LocalTime.of(22, 0), 0), new Stop(LocalTime.of(2, 0), null, 0)))));
		t.check("A stop without any time doesn't crash", in("stops", "-08:00, (no times), 10:00-"), 3,
				() -> line("-08:00", "-", "10:00-").arrival().length);
		t.done();
	}

	private static List<Integer> days(Timeline tl) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < tl.arrival().length; i++) {
			out.add(tl.day(i));
		}
		return out;
	}

	@Test
	void datesAndDurations() {
		CaseTable t = CaseTable.of("Timetable", "Run date, boarding date, departure and journey time");
		Timeline tl = line("-22:00", "02:00-02:10", "06:00-");
		LocalDate run = LocalDate.of(2026, 12, 10);
		t.check("Boarding the Day 2 stop: date is the next day", in("run date", run, "stop", 1), LocalDate.of(2026, 12, 11),
				() -> tl.boardingDate(run, 1));
		t.check("Run date for a passenger boarding the Day 2 stop on 11 Dec", in("boarding date", "2026-12-11", "stop", 1), run,
				() -> tl.runDate(LocalDate.of(2026, 12, 11), 1));
		t.check("Run date for the first stop is the boarding date", in("boarding date", "2026-12-10", "stop", 0), run, () -> tl.runDate(run, 0));
		t.check("Leaves the Day 2 stop", in("run date", run, "stop", 1), LocalDateTime.parse("2026-12-11T02:10"), () -> tl.departs(run, 1));
		t.check("Arrives at the last stop", in("run date", run, "stop", 2), LocalDateTime.parse("2026-12-11T06:00"), () -> tl.arrives(run, 2));
		t.check("Whole journey: 8 hours", in("from", 0, "to", 2), 480L, () -> tl.minutes(0, 2));
		t.check("From the Day 2 stop", in("from", 1, "to", 2), 230L, () -> tl.minutes(1, 2));
		t.check("Run date across a month end", in("boarding date", "2027-01-01", "stop", 1), LocalDate.of(2026, 12, 31),
				() -> tl.runDate(LocalDate.of(2027, 1, 1), 1));
		t.done();
	}

	@Test
	void rateLimits() {
		CaseTable t = CaseTable.of("Rate limits", "Requests per client per minute (token bucket)");
		t.check("3 per minute: first 3 allowed, 4th refused", in("limit", "3/min", "requests", "4 at the same moment"),
				List.of(true, true, true, false), () -> {
					RateLimiter r = new RateLimiter(3);
					return List.of(r.allow("a", 0), r.allow("a", 0), r.allow("a", 0), r.allow("a", 0));
				});
		t.check("One more allowed 20 seconds later", in("limit", "3/min", "requests", "3 at 0 s, then 1 at 20 s"), true, () -> {
			RateLimiter r = new RateLimiter(3);
			r.allow("a", 0);
			r.allow("a", 0);
			r.allow("a", 0);
			return r.allow("a", 20_000);
		});
		t.check("But not at 19 seconds", in("limit", "3/min", "requests", "3 at 0 s, then 1 at 19 s"), false, () -> {
			RateLimiter r = new RateLimiter(3);
			r.allow("a", 0);
			r.allow("a", 0);
			r.allow("a", 0);
			return r.allow("a", 19_000);
		});
		t.check("Other clients have their own count", in("limit", "1/min", "requests", "a, then b"), List.of(true, true), () -> {
			RateLimiter r = new RateLimiter(1);
			return List.of(r.allow("a", 0), r.allow("b", 0));
		});
		t.check("A long pause doesn't save up more than the limit", in("limit", "3/min", "requests", "4 after 10 minutes idle"),
				List.of(true, true, true, false), () -> {
					RateLimiter r = new RateLimiter(3);
					r.allow("a", 0);
					long later = 600_000;
					return List.of(r.allow("a", later), r.allow("a", later), r.allow("a", later), r.allow("a", later));
				});
		int[][] retry = { { 20, 3 }, { 120, 1 }, { 7, 9 }, { 60, 1 } };
		for (int[] c : retry) {
			int per = c[0];
			t.check("Retry-After with " + per + " per minute", in("limit", per + "/min"), c[1], () -> new RateLimiter(per).retryAfterSeconds());
		}
		t.done();
	}
}
