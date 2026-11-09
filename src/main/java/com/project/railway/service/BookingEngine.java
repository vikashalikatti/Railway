package com.project.railway.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.project.railway.dto.Booking;
import com.project.railway.dto.ChartRun;
import com.project.railway.dto.CoachSeat;
import com.project.railway.dto.Customer;
import com.project.railway.dto.Passenger;
import com.project.railway.dto.PassengerRequest;
import com.project.railway.dto.SeatOccupancy;
import com.project.railway.dto.WaitlistCounter;
import com.project.railway.helper.BookingException;
import com.project.railway.helper.CoachLayout;
import com.project.railway.helper.CompanionRules;
import com.project.railway.helper.Quota;
import com.project.railway.helper.TravelRules;
import com.project.railway.helper.SeatPicker;
import com.project.railway.helper.Seat_type;
import com.project.railway.repository.Book_Repository;
import com.project.railway.repository.ChartRun_Repository;
import com.project.railway.repository.CoachSeat_Repository;
import com.project.railway.repository.Customer_Repository;
import com.project.railway.repository.Passenger_Repository;
import com.project.railway.repository.SeatOccupancy_Repository;
import com.project.railway.repository.WaitlistCounter_Repository;

/**
 * The part of booking that touches the seat inventory. Every public method is ONE
 * database transaction:
 *
 * - READ COMMITTED: every statement sees the latest committed data, and MySQL takes no
 *   gap locks, so concurrent bookings block each other as little as possible.
 * - Seats are locked with SELECT ... FOR UPDATE [SKIP LOCKED] before they are given out.
 *   Nobody can add a SeatOccupancy for a seat without holding that seat's lock.
 * - The waitlist counter row is locked (FOR UPDATE) before anyone joins or leaves the
 *   waitlist, so WL numbers and promotions happen strictly in order.
 * - Lock order is always: seats -> waitlist counter -> waitlisted passengers.
 *
 * Callers should use BookingService, which validates input and retries on deadlocks.
 */
@Service
public class BookingEngine {

	/** Extra seats locked in the chosen coach, so the seat picker has berths to choose from */
	static final int SLACK = 4;
	/** Largest waitlist per train, date and class; beyond it the class shows REGRET */
	public static final int MAX_WAITLIST = 100;

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	@Autowired
	private CoachSeat_Repository coachSeatRepository;
	@Autowired
	private SeatOccupancy_Repository occupancyRepository;
	@Autowired
	private Passenger_Repository passengerRepository;
	@Autowired
	private WaitlistCounter_Repository counterRepository;
	@Autowired
	private Book_Repository bookingRepository;
	@Autowired
	private Customer_Repository customerRepository;
	@Autowired
	private ChartRun_Repository chartRepository;
	@Autowired
	private TicketMailer mailer;

	/* ------------------------------------------------------------------ booking */

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
	public Booking book(int customerId, JourneyPlan plan, List<PassengerRequest> requests) {
		Customer customer = customerRepository.getReferenceById(customerId);

		// Infants, and children 5-11 who chose no berth, travel with the group without a seat
		long needSeats = requests.stream()
				.filter(r -> !TravelRules.travelsWithoutBerth(r.getAge(), r.isNoBerth())).count();

		// Who looks for company: a solo woman (women's bays), a senior asking for help (helpers), a helper (seniors)
		List<SeatPicker.Traveller> travellers = new ArrayList<>();
		int anySeeks = 0;
		for (int i = 0; i < requests.size(); i++) {
			PassengerRequest r = requests.get(i);
			if (!TravelRules.travelsWithoutBerth(r.getAge(), r.isNoBerth())) {
				int seeks = CompanionRules.seeks(r.getAge(), r.getGender(), r.isNeedsAssistance(), r.isHelper(),
						(int) needSeats);
				anySeeks |= seeks;
				travellers.add(new SeatPicker.Traveller(i, r.getAge(), r.getBerthPreference(), seeks));
			}
		}
		Map<String, Integer> near = anySeeks == 0 ? Map.of() : nearFlags(plan);

		// 1. Lock free seats for this segment (may lock a few more than needed, see SLACK)
		List<CoachSeat> locked = lockFreeSeats(plan, (int) needSeats, true, anySeeks, near);

		// 2. Decide who sits where
		Map<Long, CoachSeat> seatsById = locked.stream()
				.collect(Collectors.toMap(CoachSeat::getId, s -> s, (a, b) -> a, LinkedHashMap::new));
		Map<Integer, SeatPicker.Seat> assigned = SeatPicker.assign(travellers, toPickerSeats(locked, plan.seatType(), near));
		Map<Integer, Integer> seeksByIndex = travellers.stream()
				.collect(Collectors.toMap(SeatPicker.Traveller::index, SeatPicker.Traveller::seeks));

		// 3. Build the PNR
		Booking booking = new Booking();
		booking.setCustomer(customer);
		booking.setTrainNumber(plan.trainNumber());
		booking.setTrainName(plan.trainName());
		booking.setFromStation(plan.fromStation());
		booking.setToStation(plan.toStation());
		booking.setFromStop(plan.fromStop());
		booking.setToStop(plan.toStop());
		booking.setJourneyDate(plan.date());
		booking.setSeatType(plan.seatType().name());
		booking.setQuota(plan.quota().name());
		booking.setDistanceKm(plan.distanceKm());
		double total = 0;
		booking.setBookedAt(LocalDateTime.now(IST));

		List<Passenger> waiting = new ArrayList<>();
		for (int i = 0; i < requests.size(); i++) {
			PassengerRequest r = requests.get(i);
			Passenger p = new Passenger();
			p.setBooking(booking);
			p.setName(r.getName().trim());
			p.setAge(r.getAge());
			p.setGender(r.getGender());
			p.setBerthPreference(r.getBerthPreference());
			boolean noBerth = TravelRules.travelsWithoutBerth(r.getAge(), r.isNoBerth());
			TravelRules.Meal meal = TravelRules.Meal.parse(r.getMeal());
			p.setNoBerth(noBerth);
			p.setMeal(meal.name());
			p.setBedroll(r.isBedroll() && TravelRules.bedrollOffered(plan.seatType()));
			p.setFare(TravelRules.ticketFare(r.getAge(), r.isNoBerth(), plan.farePerPassenger()));
			p.setExtrasFare(TravelRules.extras(meal, Boolean.TRUE.equals(p.getBedroll()), plan.seatType()));
			p.setNeedsAssistance(r.isNeedsAssistance());
			p.setHelper(r.isHelper());
			total += p.getFare() + p.getExtrasFare();
			SeatPicker.Seat seat = assigned.get(i);
			if (noBerth) {
				p.setStatus(Passenger.NO_BERTH); // travels with the group; no seat, no waitlist number
			} else if (seat != null) {
				confirm(p, seatsById.get(seat.id()));
				if ((seeksByIndex.getOrDefault(i, 0) & seat.near() & SeatPicker.Near.WOMEN) != 0) {
					p.setNearWomen(true);
				}
			} else {
				waiting.add(p);
			}
			booking.getPassengers().add(p);
			booking.setTotalFare(total);
		}

		// 4. Anyone without a seat joins this quota's waitlist (counter row locked -> numbers never repeat)
		if (!waiting.isEmpty()) {
			int seated = (int) needSeats - waiting.size();
			if (!plan.waitlist()) {
				String left = seated == 0 ? "No seats are left" : "Only " + seated + (seated == 1 ? " seat is" : " seats are") + " left";
				throw new BookingException(HttpStatus.CONFLICT, plan.anyQuota()
						? left + ", and the waitlist closed when the chart was prepared."
						: left + " in " + plan.quota().label + " quota, which has no waitlist. Try General quota.");
			}
			String key = plan.quota().counterKey(plan.seatType());
			WaitlistCounter counter = lockCounter(plan.trainNumber(), plan.date(), key);
			long alreadyWaiting = passengerRepository.countWaitlisted(plan.trainNumber(), plan.date(),
					plan.seatType().name(), plan.quota().name());
			if (alreadyWaiting + waiting.size() > MAX_WAITLIST) {
				throw new BookingException(HttpStatus.CONFLICT,
						"Not enough seats, and the waitlist for this class is full (REGRET). Try another class or date.");
			}
			for (Passenger p : waiting) {
				counter.setLastNumber(counter.getLastNumber() + 1);
				p.setStatus(Passenger.WAITLISTED);
				p.setWlNumber(counter.getLastNumber());
			}
		}

		booking.refreshStatus();
		bookingRepository.save(booking); // cascades to passengers

		// 5. Mark the seats taken for this segment
		List<SeatOccupancy> occupancies = new ArrayList<>();
		for (Passenger p : booking.getPassengers()) {
			if (Passenger.CONFIRMED.equals(p.getStatus())) {
				occupancies.add(occupancy(p, plan.trainNumber(), plan.date(), plan.fromStop(), plan.toStop()));
			}
		}
		occupancyRepository.saveAllAndFlush(occupancies); // flush now so any DB error happens inside this method
		if (booking.getPassengers().stream().anyMatch(p -> Passenger.CONFIRMED.equals(p.getStatus())
				&& (Boolean.TRUE.equals(p.getNeedsAssistance()) || Boolean.TRUE.equals(p.getHelper())))) {
			passengerRepository.flush();
			matchCompanions(plan.trainNumber(), plan.date());
		}
		mailer.sendAfterCommit(mailer.ticket(booking, TicketMailer.Event.BOOKED));
		return booking;
	}

	/* ------------------------------------------------------------- cancellation */

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
	public Booking cancel(long pnr, int customerId, List<Long> passengerIds) {
		Booking booking = bookingRepository.findById(pnr)
				.orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "No booking with PNR " + pnr + "."));
		if (booking.getCustomer().getId() != customerId) {
			throw new BookingException(HttpStatus.NOT_FOUND, "No booking with PNR " + pnr + ".");
		}
		if (booking.getJourneyDate().isBefore(LocalDate.now(IST))) {
			throw new BookingException(HttpStatus.BAD_REQUEST, "This journey date has passed, so it can't be cancelled.");
		}

		List<Passenger> targets = booking.getPassengers().stream()
				.filter(p -> !Passenger.CANCELLED.equals(p.getStatus()))
				.filter(p -> passengerIds == null || passengerIds.isEmpty() || passengerIds.contains(p.getId()))
				.collect(Collectors.toList());
		if (targets.isEmpty()) {
			throw new BookingException(HttpStatus.BAD_REQUEST, "There's nothing left to cancel on this booking.");
		}

		List<Passenger> confirmed = targets.stream().filter(p -> Passenger.CONFIRMED.equals(p.getStatus()))
				.collect(Collectors.toList());

		// Lock order: seats first, then the waitlist counter (same order as booking)
		Set<Long> seatIds = confirmed.stream().map(p -> p.getSeat().getId()).collect(Collectors.toSet());
		if (!seatIds.isEmpty()) {
			coachSeatRepository.lockByIds(seatIds);
		}
		Seat_type cancelType = Seat_type.valueOf(booking.getSeatType());
		Quota quota = Quota.parse(booking.getQuota());
		lockCounter(booking.getTrainNumber(), booking.getJourneyDate(), quota.counterKey(cancelType));

		if (!confirmed.isEmpty()) {
			occupancyRepository
					.deleteByPassengerIds(confirmed.stream().map(Passenger::getId).collect(Collectors.toList()));
		}
		boolean unlinked = false;
		for (Passenger p : targets) {
			p.setStatus(Passenger.CANCELLED);
			p.setCancelReason(Passenger.CANCELLED_BY_USER);
			p.setSeat(null); // coach and seat number are kept on the passenger for the record
			unlinked |= unlinkCompanion(p);
		}
		cancelOrphanedNoBerth(booking, Passenger.CANCELLED_BY_USER);
		booking.refreshStatus();
		bookingRepository.saveAndFlush(booking);
		mailer.sendAfterCommit(mailer.ticket(booking, TicketMailer.Event.CANCELLED));
		if (unlinked) {
			passengerRepository.flush();
			matchCompanions(booking.getTrainNumber(), booking.getJourneyDate()); // the other one may find someone else
		}

		if (!confirmed.isEmpty()) {
			// Before the chart a freed seat stays in its quota, so that quota's waitlist gets it.
			// After the chart nobody is waiting any more.
			if (quota.hasWaitlist) {
				promoteWaitlist(booking.getTrainNumber(), booking.getJourneyDate(), cancelType, quota, false, true);
			}
		}
		return booking;
	}

	/**
	 * Gives free seats to one quota's waitlisted passengers, lowest WL number first.
	 * anyQuota: seats of every quota may be used (first chart). notify: email the passengers
	 * who got a seat, once the transaction commits.
	 * Caller must hold that quota's waitlist counter lock. Returns the bookings that changed.
	 */
	private Set<Booking> promoteWaitlist(int trainNumber, LocalDate date, Seat_type type, Quota quota,
			boolean anyQuota, boolean notify) {
		List<Passenger> waiting = passengerRepository.findWaitlistForUpdate(trainNumber, date, type.name(),
				quota.name());
		Set<Booking> touched = new LinkedHashSet<>();
		for (Passenger p : waiting) {
			Booking b = p.getBooking();
			JourneyPlan plan = new JourneyPlan(trainNumber, b.getTrainName(), type, date, b.getFromStop(),
					b.getToStop(), b.getFromStation(), b.getToStation(), b.getDistanceKm(), p.getFare(), quota,
					anyQuota, false, null);
			int berths = (int) b.getPassengers().stream().filter(x -> !Boolean.TRUE.equals(x.getNoBerth())
					&& !Passenger.CANCELLED.equals(x.getStatus())).count();
			int seeks = CompanionRules.seeks(p.getAge(), p.getGender(), Boolean.TRUE.equals(p.getNeedsAssistance()),
					Boolean.TRUE.equals(p.getHelper()), berths);
			Map<String, Integer> near = seeks == 0 ? Map.of() : nearFlags(plan);
			// Never wait here: we hold the counter lock, and waiting could deadlock with a booking
			List<CoachSeat> seats = lockFreeSeats(plan, 1, false, seeks, near);
			if (seats.isEmpty()) {
				continue; // nothing free for this passenger's segment; a later passenger may fit
			}
			Map<Integer, SeatPicker.Seat> pick = SeatPicker.assign(
					List.of(new SeatPicker.Traveller(0, p.getAge(), p.getBerthPreference(), seeks)),
					toPickerSeats(seats, type, near));
			SeatPicker.Seat picked = pick.get(0);
			CoachSeat seat = seats.stream().filter(s -> s.getId() == picked.id()).findFirst().orElseThrow();
			confirm(p, seat);
			if ((seeks & picked.near() & SeatPicker.Near.WOMEN) != 0) {
				p.setNearWomen(true);
			}
			occupancyRepository.saveAndFlush(occupancy(p, trainNumber, date, b.getFromStop(), b.getToStop()));
			touched.add(b);
		}
		for (Booking b : touched) {
			b.refreshStatus();
			if (notify) {
				mailer.sendAfterCommit(mailer.ticket(b, TicketMailer.Event.CONFIRMED_FROM_WAITLIST));
			}
		}
		return touched;
	}

	/**
	 * New coaches were added for this date: give their seats to the class's waitlist,
	 * General first, then Tatkal, lowest WL number first. Passengers who get a seat are emailed.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
	public int promoteAfterNewSeats(int trainNumber, LocalDate date, Seat_type type) {
		int confirmed = 0;
		for (Quota q : List.of(Quota.GN, Quota.TQ)) {
			lockCounter(trainNumber, date, q.counterKey(type));
			long before = passengerRepository.countWaitlisted(trainNumber, date, type.name(), q.name());
			promoteWaitlist(trainNumber, date, type, q, false, true);
			passengerRepository.flush();
			confirmed += (int) (before - passengerRepository.countWaitlisted(trainNumber, date, type.name(), q.name()));
		}
		return confirmed;
	}

	/* ------------------------------------------------------------ charts */

	/**
	 * First chart for one train run:
	 *  1. unsold seats of every quota are released and given to the waitlist (General first, then Tatkal),
	 *  2. anyone still waiting is cancelled,
	 *  3. every passenger is emailed their final status (after the transaction commits).
	 * Runs once: the chart_run row is locked and its stage checked. Returns false if already prepared.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 60)
	public boolean prepareFirstChart(int trainNumber, LocalDate date) {
		chartRepository.ensureExists(trainNumber, date);
		ChartRun run = chartRepository.lock(trainNumber, date);
		if (run.getStage() >= ChartRun.FIRST) {
			return false;
		}

		// 1. Release quota seats to the waitlist
		Map<String, List<Quota>> waitingByClass = new LinkedHashMap<>();
		for (Object[] row : passengerRepository.findWaitingClasses(trainNumber, date)) {
			waitingByClass.computeIfAbsent((String) row[0], k -> new ArrayList<>()).add(Quota.parse((String) row[1]));
		}
		for (Map.Entry<String, List<Quota>> e : waitingByClass.entrySet()) {
			Seat_type type = Seat_type.valueOf(e.getKey());
			for (Quota q : List.of(Quota.GN, Quota.TQ)) { // General waitlist first, as on Indian Railways
				if (e.getValue().contains(q)) {
					lockCounter(trainNumber, date, q.counterKey(type));
					promoteWaitlist(trainNumber, date, type, q, true, false);
				}
			}
		}

		// 2. Anyone still waiting doesn't travel
		Set<Booking> changed = new LinkedHashSet<>();
		for (Passenger p : passengerRepository.findAllWaitingForUpdate(trainNumber, date)) {
			p.setStatus(Passenger.CANCELLED);
			p.setCancelReason(Passenger.CANCELLED_AT_CHART);
			changed.add(p.getBooking());
		}
		changed.forEach(b -> cancelOrphanedNoBerth(b, Passenger.CANCELLED_AT_CHART));
		changed.forEach(Booking::refreshStatus);
		passengerRepository.flush();
		matchCompanions(trainNumber, date); // seniors confirmed from the waitlist get a helper too

		run.setStage(ChartRun.FIRST);
		run.setFirstPreparedAt(LocalDateTime.now(IST));

		// 3. Everyone who still had a ticket at charting hears their final status
		for (Booking b : bookingRepository.findByTrainNumberAndJourneyDate(trainNumber, date)) {
			boolean hadTicket = b.getPassengers().stream()
					.anyMatch(p -> !Passenger.CANCELLED_BY_USER.equals(p.getCancelReason())
							&& !(Passenger.CANCELLED.equals(p.getStatus()) && p.getCancelReason() == null));
			if (hadTicket) {
				mailer.sendAfterCommit(mailer.ticket(b, TicketMailer.Event.CHART_PREPARED));
			}
		}
		return true;
	}

	/** Final chart: booking closes. Returns false if already prepared. */
	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
	public boolean prepareFinalChart(int trainNumber, LocalDate date) {
		chartRepository.ensureExists(trainNumber, date);
		ChartRun run = chartRepository.lock(trainNumber, date);
		if (run.getStage() >= ChartRun.FINAL) {
			return false;
		}
		if (run.getStage() < ChartRun.FIRST) {
			return false; // the first chart has to come first; the scheduler runs it next
		}
		run.setStage(ChartRun.FINAL);
		run.setFinalPreparedAt(LocalDateTime.now(IST));
		return true;
	}

	/* ------------------------------------------------------------ seat locking */

	/**
	 * Locks free seats for the journey's segment and returns them. May return MORE than
	 * wanted (up to SLACK extra in the group's coach) or FEWER (class nearly full).
	 *
	 * Pass 1  the coach with the most free seats that fits the group, SKIP LOCKED
	 * Pass 2  any coach of the class, SKIP LOCKED
	 * Pass 3  (only if waitIfNeeded) any coach, WAITING for seats other bookings hold,
	 *         used only when free seats exist but are all locked right now
	 *
	 * After every lock, the seats are re-checked against the latest committed bookings
	 * (findTaken). That closes the gap between "search" and "lock".
	 */
	List<CoachSeat> lockFreeSeats(JourneyPlan plan, int wanted, boolean waitIfNeeded) {
		return lockFreeSeats(plan, wanted, waitIfNeeded, 0, Map.of());
	}

	/**
	 * Same, but first (Pass 0) tries the bays that have what the travellers look for (seeks: SeatPicker.Near
	 * flags; near: bay key -> flags of the people already there), and keeps the group in that coach.
	 */
	List<CoachSeat> lockFreeSeats(JourneyPlan plan, int wanted, boolean waitIfNeeded, int seeks,
			Map<String, Integer> near) {
		int train = plan.trainNumber();
		String type = plan.seatType().name();
		List<CoachSeat> result = new ArrayList<>();
		Set<Long> exclude = new HashSet<>();
		exclude.add(-1L); // NOT IN () with an empty list is invalid SQL

		Map<Integer, Integer> freeByCoach = new LinkedHashMap<>();
		String quota = plan.quota().name();
		boolean anyQuota = plan.anyQuota();
		for (Object[] row : coachSeatRepository.countFreeByCoach(train, type, plan.date(), plan.fromStop(),
				plan.toStop(), quota, anyQuota)) {
			freeByCoach.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
		}
		if (freeByCoach.isEmpty()) {
			return result;
		}

		// Pass 0
		Integer nearCoach = null;
		if (seeks != 0 && !near.isEmpty()) {
			Set<String> bays = new HashSet<>();
			near.forEach((bay, flags) -> {
				if ((flags & seeks) != 0) {
					bays.add(bay);
				}
			});
			List<Long> ids = new ArrayList<>();
			for (Object[] row : coachSeatRepository.findFreeInClass(train, type, plan.date(), plan.fromStop(),
					plan.toStop(), quota, anyQuota)) {
				int seatNo = ((Number) row[2]).intValue();
				if (bays.contains(SeatPicker.bayKey((String) row[1], CoachLayout.bay(plan.seatType(), seatNo)))) {
					ids.add(((Number) row[0]).longValue());
					if (ids.size() >= wanted + SLACK + 4) {
						break;
					}
				}
			}
			if (!ids.isEmpty()) {
				addVerified(result, exclude, coachSeatRepository.lockFreeByIds(ids, exclude, plan.date(), plan.fromStop(),
						plan.toStop(), quota, anyQuota), plan);
				if (!result.isEmpty()) {
					int c = result.get(0).getCoach().getId();
					if (freeByCoach.getOrDefault(c, 0) >= wanted) {
						nearCoach = c;
					}
				}
			}
		}

		// Pass 1
		Integer coachId = nearCoach != null ? nearCoach : SeatPicker.pickCoach(freeByCoach, wanted);
		if (coachId != null) {
			addVerified(result, exclude, coachSeatRepository.lockFreeInCoach(coachId, exclude, plan.date(),
					plan.fromStop(), plan.toStop(), quota, anyQuota, wanted + SLACK), plan);
		}

		// Pass 2
		for (int round = 0; round < 3 && result.size() < wanted; round++) {
			List<CoachSeat> got = coachSeatRepository.lockFreeInClass(train, type, exclude, plan.date(),
					plan.fromStop(), plan.toStop(), quota, anyQuota, wanted - result.size());
			if (got.isEmpty()) {
				break;
			}
			addVerified(result, exclude, got, plan);
		}

		// Pass 3
		if (waitIfNeeded && result.size() < wanted
				&& coachSeatRepository.countFree(train, type, plan.date(), plan.fromStop(), plan.toStop(), quota,
						anyQuota) > result.size()) {
			for (int round = 0; round < 3 && result.size() < wanted; round++) {
				List<CoachSeat> got = coachSeatRepository.lockFreeInClassWaiting(train, type, exclude, plan.date(),
						plan.fromStop(), plan.toStop(), quota, anyQuota, wanted - result.size());
				if (got.isEmpty()) {
					break;
				}
				addVerified(result, exclude, got, plan);
			}
		}
		return result;
	}

	/** Keeps only the locked seats that are still free according to the latest committed data */
	private void addVerified(List<CoachSeat> result, Set<Long> exclude, List<CoachSeat> lockedNow, JourneyPlan plan) {
		if (lockedNow.isEmpty()) {
			return;
		}
		List<Long> ids = lockedNow.stream().map(CoachSeat::getId).collect(Collectors.toList());
		exclude.addAll(ids); // our own locks are not skipped by SKIP LOCKED, so exclude them explicitly
		Set<Long> taken = coachSeatRepository.findTaken(ids, plan.date(), plan.fromStop(), plan.toStop()).stream()
				.map(Number::longValue).collect(Collectors.toSet());
		for (CoachSeat s : lockedNow) {
			if (!taken.contains(s.getId())) {
				result.add(s);
			}
		}
	}

	private WaitlistCounter lockCounter(int trainNumber, LocalDate date, String seatType) {
		counterRepository.ensureExists(trainNumber, date, seatType);
		return Objects.requireNonNull(counterRepository.lock(trainNumber, date, seatType));
	}

	/** A child or infant without a berth can't travel alone: cancel them when nobody with a berth is left */
	static void cancelOrphanedNoBerth(Booking b, String reason) {
		boolean someoneTravels = b.getPassengers().stream().anyMatch(p -> Passenger.CONFIRMED.equals(p.getStatus())
				|| Passenger.WAITLISTED.equals(p.getStatus()));
		if (!someoneTravels) {
			for (Passenger p : b.getPassengers()) {
				if (Passenger.NO_BERTH.equals(p.getStatus())) {
					p.setStatus(Passenger.CANCELLED);
					p.setCancelReason(reason);
				}
			}
		}
	}

	/* ------------------------------------------------------------------ train runs */

	/**
	 * The railway cancelled this train on this date: every passenger still travelling is cancelled,
	 * their seats are freed, the booking records a full refund, and everyone is emailed.
	 * Returns how many passengers were affected.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 60)
	public int cancelRun(int trainNumber, LocalDate date, String reason) {
		int affected = 0;
		for (Booking b : bookingRepository.findByTrainNumberAndJourneyDate(trainNumber, date)) {
			List<Passenger> travelling = b.getPassengers().stream()
					.filter(p -> !Passenger.CANCELLED.equals(p.getStatus())).toList();
			if (travelling.isEmpty()) {
				continue;
			}
			List<Long> seated = travelling.stream().filter(p -> Passenger.CONFIRMED.equals(p.getStatus()))
					.map(Passenger::getId).toList();
			if (!seated.isEmpty()) {
				occupancyRepository.deleteByPassengerIds(seated);
			}
			double refund = 0;
			for (Passenger p : travelling) {
				refund += p.getFare() + (p.getExtrasFare() == null ? 0 : p.getExtrasFare());
				p.setStatus(Passenger.CANCELLED);
				p.setCancelReason(Passenger.CANCELLED_TRAIN);
				p.setSeat(null);
			}
			b.setRefundAmount((b.getRefundAmount() == null ? 0 : b.getRefundAmount()) + refund);
			b.refreshStatus();
			bookingRepository.save(b);
			affected += travelling.size();
			mailer.sendAfterCommit(mailer.ticket(b, TicketMailer.Event.TRAIN_CANCELLED, reason));
		}
		return affected;
	}

	/* ------------------------------------------------------------------ helpers */

	private static void confirm(Passenger p, CoachSeat seat) {
		p.setStatus(Passenger.CONFIRMED);
		p.setSeat(seat);
		p.setCoachNumber(seat.getCoach().getCoachNumber());
		p.setSeatNumber(seat.getSeatNumber());
		p.setBerthType(seat.getBerthType());
	}

	private static SeatOccupancy occupancy(Passenger p, int trainNumber, LocalDate date, int fromStop, int toStop) {
		SeatOccupancy o = new SeatOccupancy();
		o.setSeat(p.getSeat());
		o.setPassenger(p);
		o.setTrainNumber(trainNumber);
		o.setJourneyDate(date);
		o.setFromStop(fromStop);
		o.setToStop(toStop);
		return o;
	}

	private static List<SeatPicker.Seat> toPickerSeats(List<CoachSeat> seats, Seat_type type, Map<String, Integer> near) {
		Map<Long, Boolean> seen = new HashMap<>();
		List<SeatPicker.Seat> out = new ArrayList<>();
		for (CoachSeat s : seats) {
			if (seen.put(s.getId(), true) == null) {
				String coach = s.getCoach().getCoachNumber();
				int bay = CoachLayout.bay(type, s.getSeatNumber());
				out.add(new SeatPicker.Seat(s.getId(), coach, s.getSeatNumber(), s.getBerthType(), bay,
						near.getOrDefault(SeatPicker.bayKey(coach, bay), 0)));
			}
		}
		return out;
	}

	/** Bay key -> who is already there (women, free helpers, seniors waiting for a helper) for this segment */
	private Map<String, Integer> nearFlags(JourneyPlan plan) {
		List<CompanionRules.Neighbour> neighbours = new ArrayList<>();
		for (Object[] row : passengerRepository.findNeighbours(plan.trainNumber(), plan.date(), plan.seatType().name(),
				plan.fromStop(), plan.toStop())) {
			neighbours.add(new CompanionRules.Neighbour((String) row[0],
					CoachLayout.bay(plan.seatType(), ((Number) row[1]).intValue()), (String) row[2],
					((Number) row[3]).intValue(), truthy(row[4]), truthy(row[5]), row[6] != null));
		}
		return CompanionRules.nearFlags(neighbours);
	}

	private static boolean truthy(Object v) {
		if (v instanceof Boolean b) {
			return b;
		}
		if (v instanceof Number n) {
			return n.intValue() != 0;
		}
		if (v instanceof byte[] bytes) {
			return bytes.length > 0 && bytes[0] != 0;
		}
		return false;
	}

	/**
	 * Pairs seniors who asked for boarding help with volunteer helpers on the same coach (CompanionRules.match)
	 * and writes what each ticket shows. Passengers already matched are left alone.
	 */
	void matchCompanions(int trainNumber, LocalDate date) {
		List<Passenger> open = passengerRepository.findUnmatchedCompanionsForUpdate(trainNumber, date);
		List<CompanionRules.Candidate> seniors = new ArrayList<>();
		List<CompanionRules.Candidate> helpers = new ArrayList<>();
		Map<Long, Passenger> byId = new HashMap<>();
		for (Passenger p : open) {
			Booking b = p.getBooking();
			if (p.getCoachNumber() == null || p.getSeatNumber() == null || p.getCompanionId() != null) {
				continue;
			}
			CompanionRules.Candidate c = new CompanionRules.Candidate(p.getId(), b.getBookingId(), p.getCoachNumber(),
					CoachLayout.bay(Seat_type.valueOf(b.getSeatType()), p.getSeatNumber()), b.getFromStop(), b.getToStop());
			byId.put(p.getId(), p);
			if (Boolean.TRUE.equals(p.getNeedsAssistance())) {
				seniors.add(c);
			} else if (Boolean.TRUE.equals(p.getHelper())) {
				helpers.add(c);
			}
		}
		for (CompanionRules.Match m : CompanionRules.match(seniors, helpers)) {
			Passenger senior = byId.get(m.senior().id());
			Passenger helper = byId.get(m.helper().id());
			senior.setCompanionId(helper.getId());
			helper.setCompanionId(senior.getId());
			senior.setCompanionNote("Your helper on board: " + CompanionRules.firstName(helper.getName()) + ", "
					+ helper.getCoachNumber() + " / " + helper.getSeatNumber() + ". They'll look out for you at "
					+ senior.getBooking().getFromStation() + ".");
			helper.setCompanionNote("You're helping " + CompanionRules.firstName(senior.getName()) + " (" + senior.getAge()
					+ "), " + senior.getCoachNumber() + " / " + senior.getSeatNumber() + ", who boards at "
					+ senior.getBooking().getFromStation() + ".");
		}
	}

	/** A cancelled passenger's senior or helper goes back to unmatched. Returns true if there was one. */
	private boolean unlinkCompanion(Passenger p) {
		if (p.getCompanionId() == null) {
			return false;
		}
		passengerRepository.findById(p.getCompanionId()).ifPresent(other -> {
			if (p.getId().equals(other.getCompanionId())) {
				other.setCompanionId(null);
				other.setCompanionNote(null);
			}
		});
		p.setCompanionId(null);
		p.setCompanionNote(null);
		return true;
	}
}
