package com.project.railway.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import com.project.railway.dto.ChartRun;
import com.project.railway.dto.Customer;
import com.project.railway.dto.Passenger;
import com.project.railway.dto.PassengerRequest;
import com.project.railway.dto.Station;
import com.project.railway.dto.Train;
import com.project.railway.dto.TrainRun;
import com.project.railway.dto.TravelViews;
import com.project.railway.dto.SavedPassenger;
import com.project.railway.helper.BookingException;
import com.project.railway.helper.CoachLayout;
import com.project.railway.helper.CompanionRules;
import com.project.railway.helper.FareCalculator;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.Quota;
import com.project.railway.helper.RacRules;
import com.project.railway.helper.StopDays;
import com.project.railway.helper.WaitlistChance;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.helper.Seat_type;
import com.project.railway.helper.TatkalRules;
import com.project.railway.helper.TravelRules;
import com.project.railway.repository.Book_Repository;
import com.project.railway.repository.ChartRun_Repository;
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
	@Autowired
	private ChartRun_Repository chartRepository;
	@Autowired
	private com.project.railway.repository.TrainRun_Repository trainRunRepository;
	@Autowired
	private com.project.railway.repository.SavedPassenger_Repository savedRepository;
	@Autowired
	private PaymentService paymentService;

	/** Bookings must be paid (test gateway). Set app.payments.required=false to book without paying. */
	@org.springframework.beans.factory.annotation.Value("${app.payments.required:true}")
	private boolean paymentsRequired;

	/**
	 * A train that serves the search, with the positions of the passenger's boarding and destination stops.
	 * boardingDate: the day the passenger gets on; runDate: the day the train left its first station
	 * (earlier when boarding on Day 2 or later). departs / arrives: yyyy-MM-ddTHH:mm; minutes: journey time.
	 */
	public record TrainMatch(Train train, int fromStop, int toStop, String fromStation, String toStation,
			String boardingDate, String runDate, String departs, String arrives, long minutes, int fromDay, int toDay,
			Map<String, Long> available) {
	}

	private static final DateTimeFormatter ISO_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

	static TrainMatch match(Train t, StopDays.Timeline tl, int from, int to, LocalDate runDate) {
		List<Station> stops = t.getStations();
		return new TrainMatch(t, from, to, stops.get(from).getStationName(), stops.get(to).getStationName(),
				tl.boardingDate(runDate, from).toString(), runDate.toString(), tl.departs(runDate, from).format(ISO_MINUTE),
				tl.arrives(runDate, to).format(ISO_MINUTE), tl.minutes(from, to), tl.departureDay(from), tl.day(to),
				new java.util.LinkedHashMap<>());
	}

	/* ------------------------------------------------------------ endpoints */

	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<Availability>> availability(String token, int trainNo, String seatType,
			String date, String from, String to, String quotaText) {
		try {
			customerFrom(token);
			JourneyPlan plan = plan(trainNo, seatType, date, from, to, quotaText);
			String type = plan.seatType().name();
			Quota quota = plan.quota();
			long available = coachSeatRepository.countFree(trainNo, type, plan.date(), plan.fromStop(),
					plan.toStop(), quota.name(), plan.anyQuota());
			if (plan.anyQuota() && RacRules.hasRac(plan.seatType())) {
				// after the chart, RAC berths nobody needed are sold too
				available += coachSeatRepository.countFree(trainNo, type, plan.date(), plan.fromStop(), plan.toStop(),
						Quota.RC.name(), false);
			}
			long waitlisted = passengerRepository.countWaitlisted(trainNo, plan.date(), type, quota.name());
			long racRoom = !plan.anyQuota() && plan.waitlist() && quota.hasRac() && RacRules.hasRac(plan.seatType())
					? engine.racRoom(trainNo, plan.date(), plan.seatType(), plan.fromStop(), plan.toStop())
					: 0;
			String status;
			String chance = null;
			if (plan.closedReason() != null) {
				status = "NOT AVAILABLE";
			} else if (available > 0) {
				status = String.format("AVAILABLE-%04d", available);
			} else if (racRoom > 0 && waitlisted == 0) {
				long onRac = passengerRepository.findRacStretches(trainNo, plan.date(), type).size();
				status = "RAC " + (onRac + 1);
			} else if (!plan.waitlist()) {
				status = plan.anyQuota() ? "CHART PREPARED" : "NOT AVAILABLE";
			} else if (waitlisted >= BookingEngine.MAX_WAITLIST) {
				status = "REGRET";
			} else {
				status = quota.wlPrefix + " " + (waitlisted + 1);
				chance = chanceLabel(trainNo, plan.seatType(), plan.date(), (int) waitlisted + 1);
			}
			String note = plan.closedReason();
			if (note == null && available == 0 && !plan.waitlist()) {
				note = plan.anyQuota() ? "The chart has been prepared and no seats are free for your journey."
						: quota.label + " quota has no seats left and no waitlist. Try General quota.";
			}
			Availability a = new Availability(type, available, waitlisted, plan.farePerPassenger(), status,
					CoachLayout.preferences(plan.seatType()), quota.name(), note, plan.anyQuota(), racRoom, chance);
			return ok(a, available > 0 ? "Seats available" : "Waitlist");
		} catch (BookingException e) {
			return error(e);
		}
	}

	public ResponseEntity<ResponseStructure<Booking>> book(String token, int trainNo, String seatType, String date,
			String from, String to, String quotaText, List<PassengerRequest> passengers) {
		return book(token, trainNo, seatType, date, from, to, quotaText, passengers, null, false);
	}

	/** paymentId: a PAID test payment (required unless app.payments.required=false); vikalp: see VikalpService */
	public ResponseEntity<ResponseStructure<Booking>> book(String token, int trainNo, String seatType, String date,
			String from, String to, String quotaText, List<PassengerRequest> passengers, String paymentId,
			boolean vikalp) {
		try {
			Customer customer = customerFrom(token);
			String payment = paymentId == null || paymentId.isBlank() ? null : paymentId.trim();
			if (payment == null && paymentsRequired) {
				throw new BookingException(HttpStatus.PAYMENT_REQUIRED, "Pay for the ticket first.");
			}
			JourneyPlan plan = plan(trainNo, seatType, date, from, to, quotaText);
			if (plan.closedReason() != null) {
				throw new BookingException(HttpStatus.CONFLICT, plan.closedReason());
			}
			checkPassengers(passengers, plan.seatType());
			// infants travel on a lap and don't count towards a quota's rules or limits
			String notEligible = plan.quota().eligibility(passengers.stream().filter(p -> !TravelRules.isInfant(p.getAge()))
					.map(p -> new Quota.Traveller(p.getAge(), p.getGender(), p.isDivyang())).toList());
			if (notEligible != null) {
				throw new BookingException(HttpStatus.BAD_REQUEST, notEligible);
			}
			Booking booking;
			try {
				booking = withRetry(() -> engine.book(customer.getId(), plan, passengers, payment, vikalp));
			} catch (BookingException e) {
				// the booking didn't happen: give the money back now rather than in 30 minutes
				if (payment != null && e.getStatus() != HttpStatus.PAYMENT_REQUIRED) {
					paymentService.refundUnused(payment, "Booking failed: " + e.getMessage());
				} else if (payment != null) {
					paymentService.refundUnused(payment, "Fare changed before booking");
				}
				throw e;
			}
			String message = switch (booking.getStatus()) {
			case Booking.CONFIRMED -> "Booking confirmed. PNR " + booking.getBookingId();
			case Booking.WAITLISTED -> "All passengers are waitlisted. PNR " + booking.getBookingId();
			case Booking.RAC -> "Booked on RAC: you travel sharing a side-lower berth until one frees up. PNR "
					+ booking.getBookingId();
			default -> "Some passengers are confirmed and some on RAC or waitlisted. PNR " + booking.getBookingId();
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

	/** What cancelling would refund, passenger by passenger (nothing is changed) */
	public ResponseEntity<ResponseStructure<List<BookingEngine.RefundPreview>>> cancelPreview(String token, long pnr,
			List<Long> passengerIds) {
		try {
			Customer customer = customerFrom(token);
			List<BookingEngine.RefundPreview> rows = engine.cancelPreview(pnr, customer.getId(), passengerIds);
			double total = rows.stream().mapToDouble(BookingEngine.RefundPreview::refund).sum();
			return ok(rows, String.format(Locale.ENGLISH, "Refund ₹%,.0f", total));
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

	/* ------------------------------------------------------------ public PNR status */

	/** PNR status without logging in: train, date, class and each passenger's status, but no names */
	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<TravelViews.PnrView>> publicPnr(long pnr) {
		Booking b = bookingRepository.findById(pnr).orElse(null);
		if (b == null) {
			return error(new BookingException(HttpStatus.NOT_FOUND, "No booking with PNR " + pnr + "."));
		}
		fillWaitlistPositions(b);
		Quota quota = Quota.parse(b.getQuota());
		List<TravelViews.PnrPassenger> rows = new java.util.ArrayList<>();
		int n = 1;
		for (Passenger p : b.getPassengers()) {
			String booked = TicketMailer.bookedLabel(p, quota);
			String now = TicketMailer.currentLabel(p, quota);
			if (Passenger.WAITLISTED.equals(p.getStatus()) && p.getConfirmChance() != null) {
				now += ", chance " + p.getConfirmChance();
			}
			rows.add(new TravelViews.PnrPassenger(n++, booked, now));
		}
		return ok(new TravelViews.PnrView(b.getBookingId(), b.getTrainNumber(), b.getTrainName(), b.getFromStation(),
				b.getToStation(), (b.getBoardingDate() != null ? b.getBoardingDate() : b.getJourneyDate()).toString(), b.getSeatType(), quota.name(), b.getChartStatus(),
				b.getStatus(), b.getRefundAmount(), rows), "PNR status");
	}

	/* ------------------------------------------------------------ saved passengers */

	public static final int MAX_SAVED_PASSENGERS = 20;

	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<List<SavedPassenger>>> savedPassengers(String token) {
		try {
			Customer customer = customerFrom(token);
			return ok(savedRepository.findByCustomerOrderByNameAsc(customer), "Saved passengers");
		} catch (BookingException e) {
			return error(e);
		}
	}

	@Transactional
	public ResponseEntity<ResponseStructure<List<SavedPassenger>>> savePassenger(String token, PassengerRequest in) {
		try {
			Customer customer = customerFrom(token);
			if (in == null) {
				throw new BookingException(HttpStatus.BAD_REQUEST, "Enter the passenger's details.");
			}
			String name = in.getName() == null ? "" : in.getName().trim().replaceAll("\\s+", " ");
			if (!name.matches("[A-Za-z][A-Za-z .'-]{1,49}")) {
				throw new BookingException(HttpStatus.BAD_REQUEST, "Enter a name of 2 to 50 letters.");
			}
			if (in.getAge() < 0 || in.getAge() > 120) {
				throw new BookingException(HttpStatus.BAD_REQUEST, "Enter an age between 0 and 120.");
			}
			String gender = in.getGender() == null ? "" : in.getGender().trim().toUpperCase(Locale.ROOT);
			if (!GENDERS.contains(gender)) {
				throw new BookingException(HttpStatus.BAD_REQUEST, "Choose Male, Female or Other.");
			}
			TravelRules.Meal meal;
			try {
				meal = TravelRules.Meal.parse(in.getMeal());
			} catch (IllegalArgumentException e) {
				throw new BookingException(HttpStatus.BAD_REQUEST, "Choose No meal, Veg, Non-veg or Jain.");
			}
			List<SavedPassenger> mine = savedRepository.findByCustomerOrderByNameAsc(customer);
			SavedPassenger same = mine.stream().filter(x -> x.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
			if (same == null && mine.size() >= MAX_SAVED_PASSENGERS) {
				throw new BookingException(HttpStatus.BAD_REQUEST, "You can save up to " + MAX_SAVED_PASSENGERS + " passengers.");
			}
			SavedPassenger sp = same != null ? same : new SavedPassenger();
			sp.setCustomer(customer);
			sp.setName(name);
			sp.setAge(in.getAge());
			sp.setGender(gender.charAt(0) + gender.substring(1).toLowerCase(Locale.ROOT));
			String pref = in.getBerthPreference() == null ? "" : in.getBerthPreference().trim().toUpperCase(Locale.ROOT);
			sp.setBerthPreference(pref.isEmpty() || pref.length() > 20 ? null : pref);
			sp.setMeal(meal.name());
			savedRepository.save(sp);
			return ok(savedRepository.findByCustomerOrderByNameAsc(customer), same != null ? name + " updated" : name + " saved");
		} catch (BookingException e) {
			return error(e);
		}
	}

	@Transactional
	public ResponseEntity<ResponseStructure<List<SavedPassenger>>> deleteSavedPassenger(String token, long id) {
		try {
			Customer customer = customerFrom(token);
			SavedPassenger sp = savedRepository.findById(id).orElse(null);
			if (sp == null || sp.getCustomer().getId() != customer.getId()) {
				throw new BookingException(HttpStatus.NOT_FOUND, "That saved passenger doesn't exist.");
			}
			savedRepository.delete(sp);
			return ok(savedRepository.findByCustomerOrderByNameAsc(customer), sp.getName() + " removed");
		} catch (BookingException e) {
			return error(e);
		}
	}

	/* ------------------------------------------------------------ connecting trains */

	/** Shortest and longest wait between the two trains at the change station */
	public static final int MIN_CHANGE_MINUTES = 30;
	public static final int MAX_CHANGE_MINUTES = 8 * 60;

	private static String placeKey(Station s) {
		return s.getStationCode() != null && !s.getStationCode().isBlank() ? s.getStationCode().toUpperCase(Locale.ROOT)
				: (s.getStationName() == null ? "" : s.getStationName().trim().toLowerCase(Locale.ROOT));
	}

	/** Numbers of the trains with a stop at this place (so a search loads only those trains) */
	private Set<Integer> numbersStoppingAt(StationDirectory.Place place) {
		Set<String> codes = place.codes().isEmpty() ? Set.of("-") : place.codes();
		Set<String> names = place.names().isEmpty() ? Set.of("-") : place.names();
		return new java.util.HashSet<>(trainRepository.findNumbersStoppingAt(codes, names));
	}

	private static boolean runsOn(Train t, LocalDate date) {
		String day = date.getDayOfWeek().name();
		return t.getSchedule() != null && t.getSchedule().getRunningWeeks() != null && Arrays
				.stream(t.getSchedule().getRunningWeeks()).anyMatch(d -> d != null && d.trim().equalsIgnoreCase(day));
	}

	/**
	 * Two trains with one change, for when no train runs all the way: the first train's stop and the
	 * second train's stop are the same station, and the second leaves 30 minutes to 8 hours after
	 * the first arrives (same day). Best (earliest arrival) first, at most 10.
	 */
	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<List<TravelViews.Connection>>> connections(String token, String from, String to,
			String dateText) {
		try {
			customerFrom(token);
			LocalDate date = parseDate(dateText);
			StationDirectory.Place origin = stationDirectory.resolve(from);
			StationDirectory.Place destination = stationDirectory.resolve(to);
			LocalDateTime now = LocalDateTime.now(IST);
			Set<Integer> candidates = numbersStoppingAt(origin);
			candidates.addAll(numbersStoppingAt(destination));
			List<Train> trains = trainRepository.findAllById(candidates).stream()
					.filter(t -> t.getStations() != null && t.getStations().size() > 1).toList();
			Map<Integer, StopDays.Timeline> timelines = new java.util.HashMap<>();
			trains.forEach(t -> timelines.put(t.getTrainNumber(), Timelines.of(t)));

			record Option(TravelViews.Connection c, LocalDateTime arrives) {
			}
			List<Option> options = new java.util.ArrayList<>();
			for (Train a : trains) {
				List<Station> as = a.getStations();
				StopDays.Timeline ta = timelines.get(a.getTrainNumber());
				for (int i = 0; i < as.size(); i++) {
					if (!origin.matches(as.get(i))) {
						continue;
					}
					LocalDate runA = ta.runDate(date, i);
					if (!runsOn(a, runA) || cancelled(a.getTrainNumber(), runA) || !ta.departs(runA, i).isAfter(now)) {
						break;
					}
					for (int j = i + 1; j < as.size(); j++) {
						Station change = as.get(j);
						if (destination.matches(change)) {
							break; // a direct train, not a change
						}
						LocalDateTime reach = ta.arrives(runA, j);
						for (Train b : trains) {
							if (b.getTrainNumber() == a.getTrainNumber()) {
								continue;
							}
							List<Station> bs = b.getStations();
							StopDays.Timeline tb = timelines.get(b.getTrainNumber());
							for (int k = 0; k < bs.size() - 1; k++) {
								if (!placeKey(bs.get(k)).equals(placeKey(change))) {
									continue;
								}
								int m = -1;
								for (int x = k + 1; x < bs.size() && m < 0; x++) {
									if (destination.matches(bs.get(x))) {
										m = x;
									}
								}
								if (m < 0) {
									break;
								}
								// the run of train b that leaves the change station soonest after a gets there
								for (LocalDate boardB : List.of(reach.toLocalDate(), reach.toLocalDate().plusDays(1))) {
									LocalDate runB = tb.runDate(boardB, k);
									LocalDateTime leaveB = tb.departs(runB, k);
									long wait = java.time.Duration.between(reach, leaveB).toMinutes();
									if (wait < MIN_CHANGE_MINUTES || wait > MAX_CHANGE_MINUTES || !runsOn(b, runB)
											|| cancelled(b.getTrainNumber(), runB)) {
										continue;
									}
									options.add(new Option(new TravelViews.Connection(match(a, ta, i, j, runA),
											match(b, tb, k, m, runB), change.getStationName(), wait), tb.arrives(runB, m)));
									break;
								}
								break;
							}
						}
					}
					break; // first matching boarding stop of this train
				}
			}
			options.sort(java.util.Comparator.comparing(Option::arrives)
					.thenComparingLong(o -> o.c().layoverMinutes()));
			List<TravelViews.Connection> out = options.stream().map(Option::c).limit(10).toList();
			return ok(out, out.size() + " connecting journeys");
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

			List<TrainMatch> matches = new java.util.ArrayList<>();
			Set<Integer> candidates = numbersStoppingAt(origin);
			candidates.retainAll(numbersStoppingAt(destination));
			List<Train> found = new java.util.ArrayList<>(trainRepository.findAllById(candidates));
			LocalDateTime now = LocalDateTime.now(IST);
			for (Train train : found) {
				List<Station> stops = train.getStations();
				if (stops == null || train.getSchedule() == null || train.getSchedule().getRunningWeeks() == null) {
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
				// date is the day the passenger boards; on Day 2+ stops the train left its first station earlier
				StopDays.Timeline tl = Timelines.of(stops);
				LocalDate runDate = tl.runDate(date, fromStop);
				if (!runsOn(train, runDate) || !tl.departs(runDate, fromStop).isAfter(now)) {
					continue; // doesn't run then, or already left
				}
				TrainMatch m = match(train, tl, fromStop, toStop, runDate);
				// free General-quota berths per class, for the "seats available" filter and the class tiles
				if (train.getCoaches() != null) {
					for (String type : train.getCoaches().stream().map(c -> c.getCoachType()).distinct().toList()) {
						m.available().put(type, coachSeatRepository.countFree(train.getTrainNumber(), type, runDate, fromStop,
								toStop, Quota.GN.name(), false));
					}
				}
				matches.add(m);
			}
			matches.sort(java.util.Comparator.comparing(TrainMatch::departs));
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
		if (Boolean.TRUE.equals(customer.getBlocked())) {
			throw new BookingException(HttpStatus.FORBIDDEN, "Your account is blocked. Contact support.");
		}
		return customer;
	}

	/** Checks the train, class, date and stations, and works out distance and fare */
	JourneyPlan plan(int trainNo, String seatTypeText, String dateText, String from, String to, String quotaText) {
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

		// date is the day the passenger boards; seats, charts and runs belong to the day the train left its
		// first station, which is earlier when boarding on Day 2 or later
		LocalDate boardingDate = date;
		StopDays.Timeline timeline = Timelines.of(stops);
		date = timeline.runDate(boardingDate, fromStop);
		DayOfWeek day = date.getDayOfWeek();
		boolean runs = Arrays.stream(train.getSchedule().getRunningWeeks())
				.anyMatch(d -> d != null && d.trim().equalsIgnoreCase(day.name()));
		if (!runs) {
			throw new BookingException(HttpStatus.BAD_REQUEST, "This train doesn't leave "
					+ stops.get(fromStop).getStationName() + " on that date"
					+ (timeline.departureDay(fromStop) > 1 ? " (it reaches there on Day " + timeline.departureDay(fromStop)
							+ " of a run that starts on a " + day.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + ")" : "")
					+ ".");
		}
		if (!timeline.departs(date, fromStop).isAfter(LocalDateTime.now(IST))) {
			throw new BookingException(HttpStatus.BAD_REQUEST,
					"This train has already left " + stops.get(fromStop).getStationName() + ".");
		}

		double distance = 0;
		for (int i = fromStop + 1; i <= toStop; i++) {
			distance += stops.get(i).getKm();
		}
		TrainRun runStatus = trainRunRepository.findByTrainNumberAndJourneyDate(trainNo, date);
		if (runStatus != null && runStatus.isCancelled()) {
			throw new BookingException(HttpStatus.CONFLICT, "Train " + trainNo + " is cancelled on this date"
					+ (runStatus.getCancelReason() != null ? ": " + runStatus.getCancelReason() : "") + ".");
		}

		Quota quota;
		try {
			quota = Quota.parse(quotaText);
		} catch (IllegalArgumentException e) {
			throw new BookingException(HttpStatus.BAD_REQUEST, "Unknown quota " + quotaText + ".");
		}
		if (!quota.bookable()) {
			throw new BookingException(HttpStatus.BAD_REQUEST, quota.eligibility(List.of()));
		}
		if (!quota.offeredIn(seatType)) {
			throw new BookingException(HttpStatus.BAD_REQUEST,
					quota.label + " quota isn't offered in " + CoachLayout.shortCode(seatType) + ".");
		}

		// Charts: the first chart (4 hours before the train starts) frees every quota's unsold seats and
		// ends the waitlist; the final chart (30 minutes before the passenger's train leaves) ends booking.
		LocalDateTime now = LocalDateTime.now(IST);
		ChartRun run = chartRepository.findByTrainNumberAndJourneyDate(trainNo, date);
		int stage = run == null ? ChartRun.NOT_PREPARED : run.getStage();
		boolean firstChartDue = !now.isBefore(TatkalRules.firstChartAt(timeline.departs(date, 0)));

		String closed = null;
		boolean anyQuota = false;
		boolean waitlist = quota.hasWaitlist;
		if (!now.isBefore(TatkalRules.finalChartAt(timeline.departs(date, fromStop)))) {
			closed = "Booking has closed: the final chart is prepared 30 minutes before departure.";
		} else if (firstChartDue || stage >= ChartRun.FIRST) {
			if (stage < ChartRun.FIRST) {
				closed = "The chart for this train is being prepared. Try again in a minute.";
			} else {
				anyQuota = true; // every quota's unsold seats are open now
				waitlist = false;
				if (quota != Quota.GN) {
					closed = "The chart has been prepared, so only General quota is open for the seats still free.";
				}
			}
		} else if (quota.isTatkal()) {
			LocalDateTime opens = TatkalRules.opensAt(date, seatType);
			if (now.isBefore(opens)) {
				closed = quota.label + " booking for this date opens on "
						+ opens.format(DateTimeFormatter.ofPattern("d MMM 'at' HH:mm", Locale.ENGLISH)) + ".";
			}
		}
		if (coachSeatRepository.countSeatsInClassOn(trainNo, seatType.name(), date) == 0) {
			throw new BookingException(HttpStatus.BAD_REQUEST, "This train has no " + CoachLayout.shortCode(seatType)
					+ " coaches on " + date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) + ".");
		}
		if (closed == null && !anyQuota && quota != Quota.GN
				&& coachSeatRepository.countSeatsInQuotaOn(trainNo, seatType.name(), quota.name(), date) == 0) {
			closed = "This train has no " + quota.label + " seats in " + CoachLayout.shortCode(seatType) + ".";
		}

		double baseFare = train.getRoute() != null ? train.getRoute().getPrice() : 0;
		double fare = FareCalculator.perPassenger(seatType, distance, baseFare);
		if (quota == Quota.PT) {
			long total = coachSeatRepository.countSeatsInQuotaOn(trainNo, seatType.name(), Quota.PT.name(), date);
			long free = coachSeatRepository.countFree(trainNo, seatType.name(), date, fromStop, toStop,
					Quota.PT.name(), false);
			fare = TatkalRules.fare(quota, seatType, fare, Math.max(0, total - free), total);
		} else {
			fare = TatkalRules.fare(quota, seatType, fare, 0, 0);
		}

		return new JourneyPlan(trainNo, train.getTrainName(), seatType, date, fromStop, toStop,
				stops.get(fromStop).getStationName(), stops.get(toStop).getStationName(), distance, fare, quota,
				anyQuota, waitlist, closed, timeline.boardingDate(date, fromStop));
	}

	private void checkPassengers(List<PassengerRequest> passengers, Seat_type type) {
		if (passengers == null || passengers.isEmpty()) {
			throw new BookingException(HttpStatus.BAD_REQUEST, "Add at least one passenger.");
		}
		long infants = passengers.stream().filter(p -> TravelRules.isInfant(p.getAge())).count();
		if (passengers.size() - infants > MAX_PASSENGERS) {
			throw new BookingException(HttpStatus.BAD_REQUEST,
					"You can book up to " + MAX_PASSENGERS + " passengers at a time, plus " + TravelRules.MAX_INFANTS
							+ " children under 5.");
		}
		if (infants > TravelRules.MAX_INFANTS) {
			throw new BookingException(HttpStatus.BAD_REQUEST,
					"Up to " + TravelRules.MAX_INFANTS + " children under 5 can travel on one booking.");
		}
		boolean someoneWithBerth = passengers.stream()
				.anyMatch(p -> !TravelRules.travelsWithoutBerth(p.getAge(), p.isNoBerth()));
		if (!someoneWithBerth) {
			throw new BookingException(HttpStatus.BAD_REQUEST,
					"At least one passenger needs a berth: children without a berth travel with someone who has one.");
		}
		int withBerth = (int) passengers.stream()
				.filter(p -> !TravelRules.travelsWithoutBerth(p.getAge(), p.isNoBerth())).count();
		boolean asksForHelp = passengers.stream().anyMatch(PassengerRequest::isNeedsAssistance);
		for (int i = 0; i < passengers.size(); i++) {
			PassengerRequest p = passengers.get(i);
			if (p.isNeedsAssistance() && !CompanionRules.canAskForAssistance(p.getAge(), withBerth)) {
				throw new BookingException(HttpStatus.BAD_REQUEST, "Passenger " + (i + 1)
						+ ": boarding help is for passengers aged 60 or over travelling alone.");
			}
			if (p.isHelper() && !CompanionRules.canHelp(p.getAge())) {
				throw new BookingException(HttpStatus.BAD_REQUEST,
						"Passenger " + (i + 1) + ": helpers must be 18 to 59 years old.");
			}
			if (p.isHelper() && asksForHelp) {
				throw new BookingException(HttpStatus.BAD_REQUEST,
						"Passenger " + (i + 1) + " can't volunteer as a helper on a ticket that asks for help.");
			}
			if (p.isDivyang()) {
				String card = p.getConcessionCard() == null ? "" : p.getConcessionCard().trim();
				if (!card.matches("[A-Za-z0-9/-]{6,20}")) {
					throw new BookingException(HttpStatus.BAD_REQUEST,
							"Passenger " + (i + 1) + ": enter the disability concession card number (6 to 20 letters and digits).");
				}
			}
			if (p.isNoBerth() && p.getAge() >= TravelRules.CHILD_UNDER) {
				throw new BookingException(HttpStatus.BAD_REQUEST,
						"Passenger " + (i + 1) + ": only children aged 5 to 11 can choose no berth.");
			}
			try {
				TravelRules.Meal.parse(p.getMeal());
			} catch (IllegalArgumentException e) {
				throw new BookingException(HttpStatus.BAD_REQUEST, "Passenger " + (i + 1) + ": choose No meal, Veg, Non-veg or Jain.");
			}
			if (p.isBedroll() && !TravelRules.bedrollOffered(type)) {
				p.setBedroll(false); // AC classes already include one; sitting classes have none
			}
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
			if (p.getAge() < 0 || p.getAge() > 120) {
				throw new BookingException(HttpStatus.BAD_REQUEST, who + "enter an age between 0 and 120.");
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
		ChartRun run = chartRepository.findByTrainNumberAndJourneyDate(b.getTrainNumber(), b.getJourneyDate());
		int stage = run == null ? ChartRun.NOT_PREPARED : run.getStage();
		b.setChartStatus(stage >= ChartRun.FINAL ? "FINAL" : stage >= ChartRun.FIRST ? "PREPARED" : "NOT_PREPARED");
		String quota = Quota.parse(b.getQuota()).name();
		for (Passenger p : b.getPassengers()) {
			if (Passenger.WAITLISTED.equals(p.getStatus()) && p.getWlNumber() != null) {
				long ahead = passengerRepository.countWaitlistedBefore(b.getTrainNumber(), b.getJourneyDate(),
						b.getSeatType(), quota, p.getWlNumber());
				p.setCurrentWlPosition((int) ahead + 1);
				if (stage < ChartRun.FIRST) {
					p.setConfirmChance(chanceLabel(b.getTrainNumber(), Seat_type.valueOf(b.getSeatType()), b.getJourneyDate(),
							(int) ahead + 1));
				}
			}
			if (Passenger.RAC.equals(p.getStatus()) && p.getRacNumber() != null) {
				long ahead = passengerRepository.countRacBefore(b.getTrainNumber(), b.getJourneyDate(), b.getSeatType(),
						p.getRacNumber());
				p.setCurrentRacPosition((int) ahead + 1);
			}
		}
		if (b.getBoardingDate() == null) {
			b.setBoardingDate(b.getJourneyDate()); // bookings made before boarding dates existed (not saved)
		}
	}

	/**
	 * Chance that waitlist position `position` is confirmed or reaches RAC by the chart, from this train and
	 * class's last 90 days, or a rule of thumb when there's too little history.
	 */
	String chanceLabel(int trainNo, Seat_type type, LocalDate date, int position) {
		LocalDate today = LocalDate.now(IST);
		long seats = coachSeatRepository.countSeatsInClassOn(trainNo, type.name(), date);
		long rac = RacRules.hasRac(type)
				? coachSeatRepository.countSeatsInQuotaOn(trainNo, type.name(), Quota.RC.name(), date) * RacRules.PER_BERTH
				: 0;
		int history = 0;
		int confirmed = 0;
		List<Object[]> rows = passengerRepository.waitlistHistory(trainNo, type.name(), today.minusDays(90),
				today.minusDays(1), Math.max(1, position - 5), position + 5);
		if (!rows.isEmpty() && rows.get(0)[0] != null) {
			history = ((Number) rows.get(0)[0]).intValue();
			confirmed = rows.get(0)[1] == null ? 0 : ((Number) rows.get(0)[1]).intValue();
		}
		WaitlistChance.Chance c = WaitlistChance.estimate(position, seats, rac, history, confirmed);
		return c.label() + " (" + c.percent() + "%)";
	}

	private boolean cancelled(int trainNo, LocalDate runDate) {
		TrainRun r = trainRunRepository.findByTrainNumberAndJourneyDate(trainNo, runDate);
		return r != null && r.isCancelled();
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
