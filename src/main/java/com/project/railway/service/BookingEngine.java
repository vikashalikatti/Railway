package com.project.railway.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.project.railway.dto.CoachSeat;
import com.project.railway.dto.Customer;
import com.project.railway.dto.Passenger;
import com.project.railway.dto.PassengerRequest;
import com.project.railway.dto.SeatOccupancy;
import com.project.railway.dto.WaitlistCounter;
import com.project.railway.helper.BookingException;
import com.project.railway.helper.CoachLayout;
import com.project.railway.helper.SeatPicker;
import com.project.railway.helper.Seat_type;
import com.project.railway.repository.Book_Repository;
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

	/* ------------------------------------------------------------------ booking */

	@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
	public Booking book(int customerId, JourneyPlan plan, List<PassengerRequest> requests) {
		Customer customer = customerRepository.getReferenceById(customerId);

		// 1. Lock free seats for this segment (may lock a few more than needed, see SLACK)
		List<CoachSeat> locked = lockFreeSeats(plan, requests.size(), true);

		// 2. Decide who sits where
		List<SeatPicker.Traveller> travellers = new ArrayList<>();
		for (int i = 0; i < requests.size(); i++) {
			PassengerRequest r = requests.get(i);
			travellers.add(new SeatPicker.Traveller(i, r.getAge(), r.getBerthPreference()));
		}
		Map<Long, CoachSeat> seatsById = locked.stream()
				.collect(Collectors.toMap(CoachSeat::getId, s -> s, (a, b) -> a, LinkedHashMap::new));
		Map<Integer, SeatPicker.Seat> assigned = SeatPicker.assign(travellers, toPickerSeats(locked, plan.seatType()));

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
		booking.setDistanceKm(plan.distanceKm());
		booking.setTotalFare(plan.farePerPassenger() * requests.size());
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
			p.setFare(plan.farePerPassenger());
			SeatPicker.Seat seat = assigned.get(i);
			if (seat != null) {
				confirm(p, seatsById.get(seat.id()));
			} else {
				waiting.add(p);
			}
			booking.getPassengers().add(p);
		}

		// 4. Anyone without a seat joins the waitlist (counter row locked -> numbers never repeat)
		if (!waiting.isEmpty()) {
			WaitlistCounter counter = lockCounter(plan.trainNumber(), plan.date(), plan.seatType().name());
			long alreadyWaiting = passengerRepository.countWaitlisted(plan.trainNumber(), plan.date(),
					plan.seatType().name());
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
		lockCounter(booking.getTrainNumber(), booking.getJourneyDate(), booking.getSeatType());

		if (!confirmed.isEmpty()) {
			occupancyRepository
					.deleteByPassengerIds(confirmed.stream().map(Passenger::getId).collect(Collectors.toList()));
		}
		for (Passenger p : targets) {
			p.setStatus(Passenger.CANCELLED);
			p.setSeat(null); // coach and seat number are kept on the passenger for the record
		}
		booking.refreshStatus();
		bookingRepository.saveAndFlush(booking);

		if (!confirmed.isEmpty()) {
			promoteWaitlist(booking.getTrainNumber(), booking.getJourneyDate(),
					Seat_type.valueOf(booking.getSeatType()));
		}
		return booking;
	}

	/**
	 * Gives freed seats to waitlisted passengers, lowest WL number first.
	 * Caller must hold the waitlist counter lock.
	 */
	private void promoteWaitlist(int trainNumber, LocalDate date, Seat_type type) {
		List<Passenger> waiting = passengerRepository.findWaitlistForUpdate(trainNumber, date, type.name());
		Set<Booking> touched = new HashSet<>();
		for (Passenger p : waiting) {
			Booking b = p.getBooking();
			JourneyPlan plan = new JourneyPlan(trainNumber, b.getTrainName(), type, date, b.getFromStop(),
					b.getToStop(), b.getFromStation(), b.getToStation(), b.getDistanceKm(), p.getFare());
			// Never wait here: we hold the counter lock, and waiting could deadlock with a booking
			List<CoachSeat> seats = lockFreeSeats(plan, 1, false);
			if (seats.isEmpty()) {
				continue; // nothing free for this passenger's segment; a later passenger may fit
			}
			Map<Integer, SeatPicker.Seat> pick = SeatPicker.assign(
					List.of(new SeatPicker.Traveller(0, p.getAge(), p.getBerthPreference())),
					toPickerSeats(seats, type));
			long seatId = pick.get(0).id();
			CoachSeat seat = seats.stream().filter(s -> s.getId() == seatId).findFirst().orElseThrow();
			confirm(p, seat);
			occupancyRepository.saveAndFlush(occupancy(p, trainNumber, date, b.getFromStop(), b.getToStop()));
			touched.add(b);
		}
		for (Booking b : touched) {
			b.refreshStatus();
		}
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
		int train = plan.trainNumber();
		String type = plan.seatType().name();
		List<CoachSeat> result = new ArrayList<>();
		Set<Long> exclude = new HashSet<>();
		exclude.add(-1L); // NOT IN () with an empty list is invalid SQL

		Map<Integer, Integer> freeByCoach = new LinkedHashMap<>();
		for (Object[] row : coachSeatRepository.countFreeByCoach(train, type, plan.date(), plan.fromStop(),
				plan.toStop())) {
			freeByCoach.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
		}
		if (freeByCoach.isEmpty()) {
			return result;
		}

		// Pass 1
		Integer coachId = SeatPicker.pickCoach(freeByCoach, wanted);
		if (coachId != null) {
			addVerified(result, exclude, coachSeatRepository.lockFreeInCoach(coachId, exclude, plan.date(),
					plan.fromStop(), plan.toStop(), wanted + SLACK), plan);
		}

		// Pass 2
		for (int round = 0; round < 3 && result.size() < wanted; round++) {
			List<CoachSeat> got = coachSeatRepository.lockFreeInClass(train, type, exclude, plan.date(),
					plan.fromStop(), plan.toStop(), wanted - result.size());
			if (got.isEmpty()) {
				break;
			}
			addVerified(result, exclude, got, plan);
		}

		// Pass 3
		if (waitIfNeeded && result.size() < wanted
				&& coachSeatRepository.countFree(train, type, plan.date(), plan.fromStop(), plan.toStop()) > result
						.size()) {
			for (int round = 0; round < 3 && result.size() < wanted; round++) {
				List<CoachSeat> got = coachSeatRepository.lockFreeInClassWaiting(train, type, exclude, plan.date(),
						plan.fromStop(), plan.toStop(), wanted - result.size());
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

	private static List<SeatPicker.Seat> toPickerSeats(List<CoachSeat> seats, Seat_type type) {
		Map<Long, Boolean> seen = new HashMap<>();
		List<SeatPicker.Seat> out = new ArrayList<>();
		for (CoachSeat s : seats) {
			if (seen.put(s.getId(), true) == null) {
				out.add(new SeatPicker.Seat(s.getId(), s.getCoach().getCoachNumber(), s.getSeatNumber(),
						s.getBerthType(), CoachLayout.bay(type, s.getSeatNumber())));
			}
		}
		return out;
	}
}
