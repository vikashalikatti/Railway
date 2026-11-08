package com.project.railway.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.annotation.Transactional;

import com.project.railway.dto.Availability;
import com.project.railway.dto.Booking;
import com.project.railway.dto.Customer;
import com.project.railway.dto.Passenger;
import com.project.railway.dto.PassengerRequest;
import com.project.railway.dto.Station;
import com.project.railway.dto.Train;
import com.project.railway.helper.BookingException;
import com.project.railway.helper.CoachLayout;
import com.project.railway.helper.FareCalculator;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.helper.Seat_type;
import com.project.railway.repository.Book_Repository;
import com.project.railway.repository.CoachSeat_Repository;
import com.project.railway.repository.Customer_Repository;
import com.project.railway.repository.Passenger_Repository;
import com.project.railway.repository.Train_Repository;

/**
 * Checks every booking request before it reaches the database, and retries a booking
 * or cancellation (up to 3 times) when MySQL reports a deadlock or lock timeout.
 */
@Service
public class BookingService {

	public static final int MAX_PASSENGERS = 6;
	public static final int ADVANCE_BOOKING_DAYS = 120;
	private static final int MAX_ATTEMPTS = 3;

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ofPattern("M/d/yyyy"),
			DateTimeFormatter.ISO_LOCAL_DATE);
	private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER");

	@Autowired
	private BookingEngine engine;
	@Autowired
	private JwtUtil jwtUtil;
	@Autowired
	private Customer_Repository customerRepository;
	@Autowired
	private Train_Repository trainRepository;
	@Autowired
	private CoachSeat_Repository coachSeatRepository;
	@Autowired
	private Passenger_Repository passengerRepository;
	@Autowired
	private Book_Repository bookingRepository;
	@Autowired
	private StationDirectory stationDirectory;

	/** A train that serves the search, with the positions of the passenger's boarding and destination stops */
	public record TrainMatch(Train train, int fromStop, int toStop, String fromStation, String toStation) {
	}

	/* ------------------------------------------------------------ endpoints */

	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<Availability>> availability(String token, int trainNo, String seatType,
			String date, String from, String to) {
		try {
			customerFrom(token);
			JourneyPlan plan = plan(trainNo, seatType, date, from, to);
			String type = plan.seatType().name();
			long available = coachSeatRepository.countFree(trainNo, type, plan.date(), plan.fromStop(),
					plan.toStop());
			long waitlisted = passengerRepository.countWaitlisted(trainNo, plan.date(), type);
			String status = available > 0 ? String.format("AVAILABLE-%04d", available)
					: waitlisted >= BookingEngine.MAX_WAITLIST ? "REGRET" : "WL " + (waitlisted + 1);
			Availability a = new Availability(type, available, waitlisted, plan.farePerPassenger(), status,
					CoachLayout.preferences(plan.seatType()));
			return ok(a, available > 0 ? "Seats available" : "Waitlist");
		} catch (BookingException e) {
			return error(e);
		}
	}

	public ResponseEntity<ResponseStructure<Booking>> book(String token, int trainNo, String seatType, String date,
			String from, String to, List<PassengerRequest> passengers) {
		try {
			Customer customer = customerFrom(token);
			JourneyPlan plan = plan(trainNo, seatType, date, from, to);
			checkPassengers(passengers, plan.seatType());
			Booking booking = withRetry(() -> engine.book(customer.getId(), plan, passengers));
			String message = switch (booking.getStatus()) {
			case Booking.CONFIRMED -> "Booking confirmed. PNR " + booking.getBookingId();
			case Booking.WAITLISTED -> "All passengers are waitlisted. PNR " + booking.getBookingId();
			default -> "Some passengers are confirmed and some waitlisted. PNR " + booking.getBookingId();
			};
			return respond(HttpStatus.CREATED, booking, message);
		} catch (BookingException e) {
			return error(e);
		}
	}

	public ResponseEntity<ResponseStructure<Booking>> cancel(String token, long pnr, List<Long> passengerIds) {
		try {
			Customer customer = customerFrom(token);
			Booking booking = withRetry(() -> engine.cancel(pnr, customer.getId(), passengerIds));
			fillWaitlistPositions(booking);
			return ok(booking, Booking.CANCELLED.equals(booking.getStatus()) ? "Booking cancelled"
					: "Selected passengers cancelled");
		} catch (BookingException e) {
			return error(e);
		}
	}

	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<Booking>> pnrStatus(String token, long pnr) {
		try {
			Customer customer = customerFrom(token);
			Booking booking = bookingRepository.findById(pnr)
					.filter(b -> b.getCustomer().getId() == customer.getId())
					.orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "No booking with PNR " + pnr + "."));
			fillWaitlistPositions(booking);
			return ok(booking, "PNR status");
		} catch (BookingException e) {
			return error(e);
		}
	}

	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<List<Booking>>> myBookings(String token) {
		try {
			Customer customer = customerFrom(token);
			List<Booking> bookings = bookingRepository.findByCustomerOrderByBookedAtDesc(customer);
			bookings.forEach(this::fillWaitlistPositions);
			return ok(bookings, bookings.size() + " bookings");
		} catch (BookingException e) {
			return error(e);
		}
	}

	/** Live station search for the From / To boxes. Public: no log-in needed. */
	public ResponseEntity<ResponseStructure<List<StationDirectory.Suggestion>>> stations(String q) {
		List<StationDirectory.Suggestion> list = stationDirectory.suggest(q);
		return ok(list, list.size() + " matches");
	}

	/**
	 * Trains between two places on a date. from / to can be a station code (SBC),
	 * a city (Bengaluru: every station in it) or a station name.
	 */
	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<List<TrainMatch>>> searchTrains(String token, String from, String to,
			String dateText) {
		try {
			customerFrom(token);
			if (from == null || from.isBlank() || to == null || to.isBlank()) {
				throw new BookingException(HttpStatus.BAD_REQUEST, "Enter both the From and To stations.");
			}
			LocalDate date = parseDate(dateText);
			LocalDate today = LocalDate.now(IST);
			if (date.isBefore(today)) {
				throw new BookingException(HttpStatus.BAD_REQUEST, "The journey date is in the past.");
			}
			if (date.isAfter(today.plusDays(ADVANCE_BOOKING_DAYS))) {
				throw new BookingException(HttpStatus.BAD_REQUEST,
						"Booking opens " + ADVANCE_BOOKING_DAYS + " days before the journey.");
			}
			StationDirectory.Place origin = stationDirectory.resolve(from);
			StationDirectory.Place destination = stationDirectory.resolve(to);
			if (origin.overlaps(destination)) {
				throw new BookingException(HttpStatus.BAD_REQUEST, "From and To can’t be the same station.");
			}

			String day = date.getDayOfWeek().name();
			List<TrainMatch> matches = new java.util.ArrayList<>();
			for (Train train : trainRepository.findAll()) {
				if (train.getSchedule() == null || train.getSchedule().getRunningWeeks() == null || Arrays
						.stream(train.getSchedule().getRunningWeeks()).noneMatch(d -> d != null && d.trim().equalsIgnoreCase(day))) {
					continue;
				}
				List<Station> stops = train.getStations();
				if (stops == null) {
					continue;
				}
				int fromStop = -1;
				for (int i = 0; i < stops.size() && fromStop < 0; i++) {
					if (origin.matches(stops.get(i))) {
						fromStop = i;
					}
				}
				if (fromStop < 0) {
					continue;
				}
				int toStop = -1;
				for (int i = fromStop + 1; i < stops.size() && toStop < 0; i++) {
					if (destination.matches(stops.get(i))) {
						toStop = i;
					}
				}
				if (toStop < 0) {
					continue;
				}
				if (date.equals(today)) {
					LocalTime departs = stops.get(fromStop).getDepartureTime();
					if (departs != null && !departs.isAfter(LocalTime.now(IST))) {
						continue; // already left today
					}
				}
				matches.add(new TrainMatch(train, fromStop, toStop, stops.get(fromStop).getStationName(),
						stops.get(toStop).getStationName()));
			}
			if (matches.isEmpty()) {
				throw new BookingException(HttpStatus.NOT_FOUND,
						"No trains run from " + origin.label() + " to " + destination.label() + " on that date.");
			}
			return ok(matches, matches.size() + " trains");
		} catch (BookingException e) {
			return error(e);
		}
	}

	/* ------------------------------------------------------------ validation */

	private Customer customerFrom(String token) {
		String[] subject = jwtUtil.getSubject(token);
		if (subject == null) {
			throw new BookingException(HttpStatus.UNAUTHORIZED, "Your session has ended. Log in again.");
		}
		if (!"customer".equals(subject[1])) {
			throw new BookingException(HttpStatus.FORBIDDEN, "Only passengers can book tickets.");
		}
		Customer customer = customerRepository.findByEmail(subject[0]);
		if (customer == null) {
			throw new BookingException(HttpStatus.UNAUTHORIZED, "Your account wasn't found. Log in again.");
		}
		return customer;
	}

	/** Checks the train, class, date and stations, and works out distance and fare */
	JourneyPlan plan(int trainNo, String seatTypeText, String dateText, String from, String to) {
		Train train = trainRepository.findByTrainNumber(trainNo);
		if (train == null) {
			throw new BookingException(HttpStatus.NOT_FOUND, "Train " + trainNo + " doesn't exist.");
		}

		Seat_type seatType;
		try {
			seatType = Seat_type.valueOf(seatTypeText.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new BookingException(HttpStatus.BAD_REQUEST, "Unknown class " + seatTypeText + ".");
		}
		boolean hasClass = train.getCoaches() != null
				&& train.getCoaches().stream().anyMatch(c -> seatType.name().equals(c.getCoachType()));
		if (!hasClass) {
			throw new BookingException(HttpStatus.BAD_REQUEST, "This train has no " + seatType.name() + " coaches.");
		}
		if (coachSeatRepository.countSeatsInClass(trainNo, seatType.name()) == 0) {
			// Coaches without seat rows would otherwise look sold out and put everyone on the waitlist
			throw new BookingException(HttpStatus.CONFLICT, "Seats haven’t been set up for this train’s " + seatType.name()
					+ " coaches yet. The admin needs to set up its coaches again.");
		}

		LocalDate date = parseDate(dateText);
		LocalDate today = LocalDate.now(IST);
		if (date.isBefore(today)) {
			throw new BookingException(HttpStatus.BAD_REQUEST, "The journey date is in the past.");
		}
		if (date.isAfter(today.plusDays(ADVANCE_BOOKING_DAYS))) {
			throw new BookingException(HttpStatus.BAD_REQUEST,
					"Booking opens " + ADVANCE_BOOKING_DAYS + " days before the journey.");
		}
		if (train.getSchedule() == null || train.getSchedule().getRunningWeeks() == null) {
			throw new BookingException(HttpStatus.BAD_REQUEST, "This train has no running days set.");
		}
		DayOfWeek day = date.getDayOfWeek();
		boolean runs = Arrays.stream(train.getSchedule().getRunningWeeks())
				.anyMatch(d -> d != null && d.trim().equalsIgnoreCase(day.name()));
		if (!runs) {
			throw new BookingException(HttpStatus.BAD_REQUEST, "This train doesn't run on "
					+ day.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + ".");
		}

		List<Station> stops = train.getStations();
		int fromStop = indexOf(stops, from, 0);
		if (fromStop < 0) {
			throw new BookingException(HttpStatus.BAD_REQUEST, "This train doesn't stop at " + from + ".");
		}
		int toStop = indexOf(stops, to, fromStop + 1);
		if (toStop < 0) {
			throw new BookingException(HttpStatus.BAD_REQUEST,
					"This train doesn't go from " + from + " to " + to + ".");
		}

		if (date.equals(today)) {
			LocalTime departs = stops.get(fromStop).getDepartureTime();
			if (departs != null && !departs.isAfter(LocalTime.now(IST))) {
				throw new BookingException(HttpStatus.BAD_REQUEST,
						"This train has already left " + stops.get(fromStop).getStationName() + " today.");
			}
		}

		double distance = 0;
		for (int i = fromStop + 1; i <= toStop; i++) {
			distance += stops.get(i).getKm();
		}
		double baseFare = train.getRoute() != null ? train.getRoute().getPrice() : 0;
		double fare = FareCalculator.perPassenger(seatType, distance, baseFare);

		return new JourneyPlan(trainNo, train.getTrainName(), seatType, date, fromStop, toStop,
				stops.get(fromStop).getStationName(), stops.get(toStop).getStationName(), distance, fare);
	}

	private void checkPassengers(List<PassengerRequest> passengers, Seat_type type) {
		if (passengers == null || passengers.isEmpty()) {
			throw new BookingException(HttpStatus.BAD_REQUEST, "Add at least one passenger.");
		}
		if (passengers.size() > MAX_PASSENGERS) {
			throw new BookingException(HttpStatus.BAD_REQUEST,
					"You can book up to " + MAX_PASSENGERS + " passengers at a time.");
		}
		List<String> allowed = CoachLayout.preferences(type);
		Set<String> names = new HashSet<>();
		for (int i = 0; i < passengers.size(); i++) {
			PassengerRequest p = passengers.get(i);
			String who = "Passenger " + (i + 1) + ": ";
			String name = p.getName() == null ? "" : p.getName().trim().replaceAll("\\s+", " ");
			if (!name.matches("[A-Za-z][A-Za-z .'-]{1,49}")) {
				throw new BookingException(HttpStatus.BAD_REQUEST, who + "enter a name of 2 to 50 letters.");
			}
			if (!names.add(name.toLowerCase(Locale.ROOT))) {
				throw new BookingException(HttpStatus.BAD_REQUEST, who + name + " is already on this booking.");
			}
			p.setName(name);
			if (p.getAge() < 1 || p.getAge() > 120) {
				throw new BookingException(HttpStatus.BAD_REQUEST, who + "enter an age between 1 and 120.");
			}
			String gender = p.getGender() == null ? "" : p.getGender().trim().toUpperCase(Locale.ROOT);
			if (!GENDERS.contains(gender)) {
				throw new BookingException(HttpStatus.BAD_REQUEST, who + "choose Male, Female or Other.");
			}
			p.setGender(gender.charAt(0) + gender.substring(1).toLowerCase(Locale.ROOT));
			String pref = p.getBerthPreference() == null ? "" : p.getBerthPreference().trim().toUpperCase(Locale.ROOT);
			if (pref.isEmpty() || pref.equals("NO_PREFERENCE")) {
				p.setBerthPreference(null);
			} else if (!allowed.contains(pref)) {
				throw new BookingException(HttpStatus.BAD_REQUEST,
						who + pref + " isn't available in " + type.name() + ". Choose one of " + allowed + ".");
			} else {
				p.setBerthPreference(pref);
			}
		}
	}

	private static LocalDate parseDate(String text) {
		if (text != null) {
			for (DateTimeFormatter f : DATE_FORMATS) {
				try {
					return LocalDate.parse(text.trim(), f);
				} catch (DateTimeParseException ignored) {
					// try the next format
				}
			}
		}
		throw new BookingException(HttpStatus.BAD_REQUEST, "Enter the date as M/d/yyyy, e.g. 11/8/2026.");
	}

	private static int indexOf(List<Station> stops, String name, int startAt) {
		if (name == null || stops == null) {
			return -1;
		}
		for (int i = startAt; i < stops.size(); i++) {
			String n = stops.get(i).getStationName();
			if (n != null && n.trim().equalsIgnoreCase(name.trim())) {
				return i;
			}
		}
		return -1;
	}

	/* ------------------------------------------------------------ helpers */

	/** Runs a booking transaction again if MySQL picked it as a deadlock victim or a lock wait timed out */
	private <T> T withRetry(Supplier<T> transaction) {
		for (int attempt = 1;; attempt++) {
			try {
				return transaction.get();
			} catch (ConcurrencyFailureException | DataIntegrityViolationException | QueryTimeoutException
					| TransactionTimedOutException e) {
				if (attempt >= MAX_ATTEMPTS) {
					throw new BookingException(HttpStatus.CONFLICT,
							"Lots of people are booking this train right now. Please try again in a moment.");
				}
				try {
					Thread.sleep(50L * attempt);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE, "Booking was interrupted. Try again.");
				}
			}
		}
	}

	private void fillWaitlistPositions(Booking b) {
		for (Passenger p : b.getPassengers()) {
			if (Passenger.WAITLISTED.equals(p.getStatus()) && p.getWlNumber() != null) {
				long ahead = passengerRepository.countWaitlistedBefore(b.getTrainNumber(), b.getJourneyDate(),
						b.getSeatType(), p.getWlNumber());
				p.setCurrentWlPosition((int) ahead + 1);
			}
		}
	}

	private static <T> ResponseEntity<ResponseStructure<T>> ok(T data, String message) {
		return respond(HttpStatus.OK, data, message);
	}

	private static <T> ResponseEntity<ResponseStructure<T>> respond(HttpStatus status, T data, String message) {
		ResponseStructure<T> body = new ResponseStructure<>();
		body.setStatus(status.value());
		body.setMessage(message);
		body.setData2(data);
		return new ResponseEntity<>(body, status);
	}

	private static <T> ResponseEntity<ResponseStructure<T>> error(BookingException e) {
		ResponseStructure<T> body = new ResponseStructure<>();
		body.setStatus(e.getStatus().value());
		body.setMessage(e.getMessage());
		return new ResponseEntity<>(body, e.getStatus());
	}
}
