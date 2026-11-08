package com.project.railway.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.railway.dto.Booking;
import com.project.railway.dto.Coach;
import com.project.railway.dto.Passenger;
import com.project.railway.dto.Station;
import com.project.railway.dto.Train;
import com.project.railway.dto.TrainRun;
import com.project.railway.dto.TravelViews;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.repository.Book_Repository;
import com.project.railway.repository.TrainRun_Repository;
import com.project.railway.repository.Train_Repository;

/**
 * What happens to a train on a date, after it is booked:
 *  - the admin cancels it (everyone refunded and emailed) or reschedules it (everyone emailed the new time)
 *  - the admin updates where it is and how late it's running; anyone can see the running status
 *  - reports: occupancy, revenue, cancellations and waitlist trends
 */
@Service
public class RunService {

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

	@Autowired
	private Train_Repository trainRepository;
	@Autowired
	private TrainRun_Repository runRepository;
	@Autowired
	private Book_Repository bookingRepository;
	@Autowired
	private BookingEngine engine;
	@Autowired
	private TicketMailer mailer;
	@Autowired
	private JwtUtil jwtUtil;

	/* ------------------------------------------------------------ admin: cancel, reschedule, position */

	/** Body: {"date": "2026-11-12", "reason": "Track maintenance"} */
	@Transactional
	public ResponseEntity<ResponseStructure<TravelViews.Running>> cancel(String token, int trainNo, Map<String, Object> body) {
		ResponseStructure<TravelViews.Running> rs = new ResponseStructure<>();
		try {
			admin(token);
			Train train = train(trainNo);
			LocalDate date = date(body.get("date"));
			if (date.isBefore(LocalDate.now(IST))) {
				throw new IllegalArgumentException("Choose today or a later date");
			}
			String reason = text(body.get("reason"), 200);
			TrainRun run = run(trainNo, date);
			if (run.isCancelled()) {
				throw new IllegalArgumentException("Train " + trainNo + " is already cancelled on " + date);
			}
			run.setCancelled(true);
			run.setCancelReason(reason);
			run.setUpdatedAt(LocalDateTime.now(IST));
			runRepository.saveAndFlush(run); // new bookings for this date are refused from now on
			int affected = engine.cancelRun(trainNo, date, reason == null ? null : "Reason: " + reason);
			rs.setData2(view(train, date, run));
			return reply(rs, HttpStatus.OK, "Train " + trainNo + " cancelled on " + date + ". " + affected
					+ " passengers were cancelled with a full refund and emailed.");
		} catch (IllegalArgumentException e) {
			return reply(rs, HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (SecurityException e) {
			return reply(rs, HttpStatus.UNAUTHORIZED, e.getMessage());
		}
	}

	/**
	 * Reschedule: the train leaves later than timetabled. Everyone booked is emailed the new time.
	 * Body: {"date": "2026-11-12", "delayMinutes": 90, "reason": "Late arrival of the rake"}
	 */
	@Transactional
	public ResponseEntity<ResponseStructure<TravelViews.Running>> reschedule(String token, int trainNo, Map<String, Object> body) {
		ResponseStructure<TravelViews.Running> rs = new ResponseStructure<>();
		try {
			admin(token);
			Train train = train(trainNo);
			LocalDate date = date(body.get("date"));
			int delay = minutes(body.get("delayMinutes"));
			String reason = text(body.get("reason"), 200);
			TrainRun run = run(trainNo, date);
			if (run.isCancelled()) {
				throw new IllegalArgumentException("This run is cancelled");
			}
			run.setDelayMinutes(delay);
			run.setNote(reason);
			run.setUpdatedAt(LocalDateTime.now(IST));
			runRepository.save(run);
			int emailed = 0;
			for (Booking b : bookingRepository.findByTrainNumberAndJourneyDate(trainNo, date)) {
				boolean travelling = b.getPassengers().stream().anyMatch(p -> !Passenger.CANCELLED.equals(p.getStatus()));
				if (!travelling) {
					continue;
				}
				Station board = train.getStations().get(Math.min(b.getFromStop(), train.getStations().size() - 1));
				String note = delay == 0 ? "The train now runs on time."
						: "New departure from " + b.getFromStation() + ": " + plus(board.getDepartureTime(), delay)
								+ " (" + delayLabel(delay) + " late)" + (reason != null ? ". Reason: " + reason : "") + ".";
				mailer.sendAfterCommit(mailer.ticket(b, TicketMailer.Event.RESCHEDULED, note));
				emailed++;
			}
			rs.setData2(view(train, date, run));
			return reply(rs, HttpStatus.OK, (delay == 0 ? "Back on time" : "Rescheduled by " + delayLabel(delay))
					+ "; " + emailed + " bookings emailed.");
		} catch (IllegalArgumentException e) {
			return reply(rs, HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (SecurityException e) {
			return reply(rs, HttpStatus.UNAUTHORIZED, e.getMessage());
		}
	}

	/**
	 * Live position: the station the train has reached or left, and how late it is. No emails.
	 * Body: {"date": "2026-11-12", "stop": 2, "departed": true, "delayMinutes": 25}
	 */
	@Transactional
	public ResponseEntity<ResponseStructure<TravelViews.Running>> position(String token, int trainNo, Map<String, Object> body) {
		ResponseStructure<TravelViews.Running> rs = new ResponseStructure<>();
		try {
			admin(token);
			Train train = train(trainNo);
			LocalDate date = date(body.get("date"));
			TrainRun run = run(trainNo, date);
			if (run.isCancelled()) {
				throw new IllegalArgumentException("This run is cancelled");
			}
			int stop;
			try {
				stop = Integer.parseInt(String.valueOf(body.get("stop")).trim());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Choose the station the train is at");
			}
			if (stop < 0 || stop >= train.getStations().size()) {
				throw new IllegalArgumentException("Choose a station on this train's route");
			}
			run.setCurrentStop(stop);
			run.setDeparted(Boolean.TRUE.equals(body.get("departed")) || "true".equals(String.valueOf(body.get("departed"))));
			if (body.get("delayMinutes") != null && !String.valueOf(body.get("delayMinutes")).isBlank()) {
				run.setDelayMinutes(minutes(body.get("delayMinutes")));
			}
			run.setUpdatedAt(LocalDateTime.now(IST));
			runRepository.save(run);
			rs.setData2(view(train, date, run));
			return reply(rs, HttpStatus.OK, "Running status updated");
		} catch (IllegalArgumentException e) {
			return reply(rs, HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (SecurityException e) {
			return reply(rs, HttpStatus.UNAUTHORIZED, e.getMessage());
		}
	}

	/** Anyone: is the train cancelled, how late, where is it */
	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<TravelViews.Running>> running(int trainNo, String dateText) {
		ResponseStructure<TravelViews.Running> rs = new ResponseStructure<>();
		try {
			Train train = train(trainNo);
			LocalDate date = date(dateText);
			TrainRun run = runRepository.findByTrainNumberAndJourneyDate(trainNo, date);
			rs.setData2(view(train, date, run));
			return reply(rs, HttpStatus.OK, "Running status");
		} catch (IllegalArgumentException e) {
			return reply(rs, HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	private TravelViews.Running view(Train train, LocalDate date, TrainRun run) {
		int delay = run == null ? 0 : run.getDelayMinutes();
		Integer current = run == null ? null : run.getCurrentStop();
		boolean departed = run != null && Boolean.TRUE.equals(run.getDeparted());
		List<TravelViews.RunningStop> stops = new ArrayList<>();
		List<Station> list = train.getStations() == null ? List.of() : train.getStations();
		for (int i = 0; i < list.size(); i++) {
			Station s = list.get(i);
			boolean passed = current != null && (i < current || (i == current && departed));
			stops.add(new TravelViews.RunningStop(s.getStationName(), s.getStationCode(), time(s.getArrivalTime()),
					time(s.getDepartureTime()), plus(s.getArrivalTime(), delay), plus(s.getDepartureTime(), delay), passed,
					current != null && i == current));
		}
		return new TravelViews.Running(train.getTrainNumber(), train.getTrainName(), date.toString(),
				run != null && run.isCancelled(), run == null ? null : run.getCancelReason(), delay, current, departed,
				run == null ? null : run.getNote(),
				run == null || run.getUpdatedAt() == null ? null : run.getUpdatedAt().format(WHEN), stops);
	}

	/* ------------------------------------------------------------ admin: reports */

	public record Totals(long bookings, long passengers, double revenue, double refunds, long cancelledPassengers,
			long waitlistedAtBooking, long confirmedFromWaitlist, long notConfirmedAtChart, long trainCancellations) {
	}

	public record ClassRow(int trainNumber, String trainName, String seatType, long seats, long confirmed,
			double occupancyPct, double revenue, long cancelled, long waitlisted) {
	}

	public record DayRow(String date, long bookings, long passengers, double revenue, long cancelled, long waitlisted,
			long confirmedFromWaitlist, long notConfirmedAtChart) {
	}

	public record Report(String from, String to, Totals totals, List<ClassRow> byClass, List<DayRow> byDay) {
	}

	/**
	 * Journeys from `from` to `to` (inclusive, at most 92 days):
	 *  - occupancy: confirmed passengers / berths on the coaches that ran, per train and class
	 *  - revenue: fares and extras of passengers not cancelled, less refunds
	 *  - waitlist: how many were waitlisted at booking, later confirmed, or not confirmed at charting
	 */
	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<Report>> report(String token, String fromText, String toText) {
		ResponseStructure<Report> rs = new ResponseStructure<>();
		try {
			admin(token);
			LocalDate from = date(fromText);
			LocalDate to = date(toText);
			if (to.isBefore(from)) {
				throw new IllegalArgumentException("The end date must be on or after the start date");
			}
			if (from.plusDays(92).isBefore(to)) {
				throw new IllegalArgumentException("Choose at most 3 months");
			}

			Map<String, long[]> counts = new LinkedHashMap<>(); // train|class -> [confirmed, cancelled, waitlisted]
			Map<String, Double> money = new HashMap<>();
			Map<String, Object[]> days = new TreeMap<>(); // date -> [long[6], double revenue]
			long bookings = 0, passengers = 0, cancelled = 0, wlAtBooking = 0, fromWl = 0, notAtChart = 0;
			double revenue = 0, refunds = 0;

			for (Booking b : bookingRepository.findByJourneyDateBetween(from, to)) {
				bookings++;
				String key = b.getTrainNumber() + "|" + b.getSeatType();
				long[] c = counts.computeIfAbsent(key, k -> new long[3]);
				Object[] day = days.computeIfAbsent(b.getJourneyDate().toString(), k -> new Object[] { new long[6], new double[1] });
				long[] dc = (long[]) day[0];
				double[] dr = (double[]) day[1];
				dc[0]++;
				double refund = b.getRefundAmount() == null ? 0 : b.getRefundAmount();
				refunds += refund;
				for (Passenger p : b.getPassengers()) {
					passengers++;
					dc[1]++;
					double paid = p.getFare() + (p.getExtrasFare() == null ? 0 : p.getExtrasFare());
					boolean gone = Passenger.CANCELLED.equals(p.getStatus());
					if (!gone) {
						revenue += paid;
						dr[0] += paid;
						money.merge(key, paid, Double::sum);
					}
					if (Passenger.CONFIRMED.equals(p.getStatus())) {
						c[0]++;
					}
					if (gone) {
						cancelled++;
						c[1]++;
						dc[2]++;
					}
					if (p.getWlNumber() != null) {
						wlAtBooking++;
						c[2]++;
						dc[3]++;
						if (Passenger.CONFIRMED.equals(p.getStatus()) || p.getSeatNumber() != null) {
							fromWl++;
							dc[4]++;
						}
						if (Passenger.CANCELLED_AT_CHART.equals(p.getCancelReason())) {
							notAtChart++;
							dc[5]++;
						}
					}
				}
			}

			long runsCancelled = runRepository.findAll().stream().filter(r -> r.isCancelled()
					&& !r.getJourneyDate().isBefore(from) && !r.getJourneyDate().isAfter(to)).count();

			// berths that ran: coaches running each day the train runs
			List<ClassRow> byClass = new ArrayList<>();
			for (Train t : trainRepository.findAll()) {
				if (t.getCoaches() == null || t.getSchedule() == null || t.getSchedule().getRunningWeeks() == null) {
					continue;
				}
				Map<String, Long> seats = new LinkedHashMap<>();
				for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
					String dayName = d.getDayOfWeek().name();
					if (Arrays.stream(t.getSchedule().getRunningWeeks()).noneMatch(x -> x != null && x.trim().equalsIgnoreCase(dayName))) {
						continue;
					}
					TrainRun r = runRepository.findByTrainNumberAndJourneyDate(t.getTrainNumber(), d);
					if (r != null && r.isCancelled()) {
						continue;
					}
					for (Coach c : t.getCoaches()) {
						if (c.runsOn(d)) {
							seats.merge(c.getCoachType(), (long) c.getNumberOfSeats(), Long::sum);
						}
					}
				}
				for (Map.Entry<String, Long> e : seats.entrySet()) {
					String key = t.getTrainNumber() + "|" + e.getKey();
					long[] c = counts.getOrDefault(key, new long[3]);
					double pct = e.getValue() == 0 ? 0 : Math.round(1000.0 * c[0] / e.getValue()) / 10.0;
					byClass.add(new ClassRow(t.getTrainNumber(), t.getTrainName(), e.getKey(), e.getValue(), c[0], pct,
							money.getOrDefault(key, 0.0), c[1], c[2]));
				}
			}

			List<DayRow> byDay = new ArrayList<>();
			days.forEach((d, v) -> {
				long[] dc = (long[]) v[0];
				byDay.add(new DayRow(d, dc[0], dc[1], ((double[]) v[1])[0], dc[2], dc[3], dc[4], dc[5]));
			});

			rs.setData2(new Report(from.toString(), to.toString(), new Totals(bookings, passengers, revenue - refunds, refunds,
					cancelled, wlAtBooking, fromWl, notAtChart, runsCancelled), byClass, byDay));
			return reply(rs, HttpStatus.OK, "Report");
		} catch (IllegalArgumentException e) {
			return reply(rs, HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (SecurityException e) {
			return reply(rs, HttpStatus.UNAUTHORIZED, e.getMessage());
		}
	}

	/* ------------------------------------------------------------ helpers */

	private void admin(String token) {
		String[] who = jwtUtil.getSubject(token);
		if (who == null || !"admin".equals(who[1])) {
			throw new SecurityException("Invalid or Expired Token, Please Login Again");
		}
	}

	private Train train(int trainNo) {
		Train t = trainRepository.findByTrainNumber(trainNo);
		if (t == null) {
			throw new IllegalArgumentException("Train " + trainNo + " doesn't exist");
		}
		if (t.getStations() == null || t.getStations().isEmpty()) {
			throw new IllegalArgumentException("Train " + trainNo + " has no stops set up yet");
		}
		return t;
	}

	private TrainRun run(int trainNo, LocalDate date) {
		TrainRun r = runRepository.findByTrainNumberAndJourneyDate(trainNo, date);
		if (r == null) {
			r = new TrainRun();
			r.setTrainNumber(trainNo);
			r.setJourneyDate(date);
		}
		return r;
	}

	private static LocalDate date(Object value) {
		try {
			return LocalDate.parse(String.valueOf(value).trim());
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("Enter the date as yyyy-MM-dd, e.g. 2026-11-12");
		}
	}

	private static int minutes(Object value) {
		int m;
		try {
			m = Integer.parseInt(String.valueOf(value).trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Enter the delay in minutes");
		}
		if (m < 0 || m > 24 * 60) {
			throw new IllegalArgumentException("The delay must be between 0 and 1440 minutes");
		}
		return m;
	}

	private static String text(Object value, int max) {
		if (value == null) {
			return null;
		}
		String s = String.valueOf(value).trim().replaceAll("\\s+", " ");
		if (s.isEmpty()) {
			return null;
		}
		return s.length() > max ? s.substring(0, max) : s;
	}

	private static String time(LocalTime t) {
		return t == null ? null : t.format(HM);
	}

	private static String plus(LocalTime t, int minutes) {
		return t == null ? null : t.plusMinutes(minutes).format(HM);
	}

	static String delayLabel(int minutes) {
		int h = minutes / 60;
		int m = minutes % 60;
		return h == 0 ? m + " min" : m == 0 ? h + " h" : h + " h " + m + " min";
	}

	private static <T> ResponseEntity<ResponseStructure<T>> reply(ResponseStructure<T> structure, HttpStatus status,
			String message) {
		structure.setMessage(message);
		structure.setStatus(status.value());
		return new ResponseEntity<>(structure, status);
	}
}
