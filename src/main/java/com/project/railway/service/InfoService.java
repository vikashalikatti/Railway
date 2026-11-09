package com.project.railway.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.railway.dto.Booking;
import com.project.railway.dto.Customer;
import com.project.railway.dto.Passenger;
import com.project.railway.dto.Station;
import com.project.railway.dto.Train;
import com.project.railway.dto.TravelViews;
import com.project.railway.helper.CoachLayout;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.Quota;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.helper.Seat_type;
import com.project.railway.helper.StopDays;
import com.project.railway.repository.Book_Repository;
import com.project.railway.repository.Customer_Repository;
import com.project.railway.repository.Train_Repository;

/** Public timetables and train lookup, and signed e-ticket codes a ticket checker can verify */
@Service
public class InfoService {

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH);

	@Autowired
	private Train_Repository trainRepository;
	@Autowired
	private Book_Repository bookingRepository;
	@Autowired
	private Customer_Repository customerRepository;
	@Autowired
	private JwtUtil jwtUtil;

	/* ------------------------------------------------------------------ timetable */

	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<TravelViews.Timetable>> timetable(int trainNo) {
		Train t = trainRepository.findByTrainNumber(trainNo);
		if (t == null || t.getStations() == null || t.getStations().isEmpty()) {
			return reply(HttpStatus.NOT_FOUND, null, "No train " + trainNo + ".");
		}
		List<Station> stops = t.getStations();
		StopDays.Timeline tl = Timelines.of(stops);
		List<TravelViews.TimetableStop> rows = new ArrayList<>();
		double km = 0;
		for (int i = 0; i < stops.size(); i++) {
			Station s = stops.get(i);
			km += i == 0 ? 0 : s.getKm();
			boolean first = i == 0;
			boolean last = i == stops.size() - 1;
			rows.add(new TravelViews.TimetableStop(i + 1, s.getStationName(), s.getStationCode(),
					first ? null : time(tl.arrival()[i]), last ? null : time(tl.departure()[i]), tl.day(i),
					first || last ? 0 : tl.departure()[i] - tl.arrival()[i], Math.round(km)));
		}
		List<String> days = t.getSchedule() == null || t.getSchedule().getRunningWeeks() == null ? List.of()
				: Arrays.stream(t.getSchedule().getRunningWeeks()).filter(d -> d != null && !d.isBlank())
						.map(d -> d.trim().substring(0, 1).toUpperCase(Locale.ROOT) + d.trim().substring(1, 3).toLowerCase(Locale.ROOT))
						.toList();
		Set<String> classes = new LinkedHashSet<>();
		if (t.getCoaches() != null) {
			t.getCoaches().forEach(c -> {
				try {
					classes.add(CoachLayout.shortCode(Seat_type.valueOf(c.getCoachType())));
				} catch (IllegalArgumentException e) {
					// old coach types
				}
			});
		}
		long minutes = tl.minutes(0, stops.size() - 1);
		return reply(HttpStatus.OK, new TravelViews.Timetable(t.getTrainNumber(), t.getTrainName(), t.getTrain_type(), days,
				new ArrayList<>(classes), rows, Math.round(km), minutes / 60 + " h " + minutes % 60 + " min"), "Timetable");
	}

	/** Trains by number (prefix) or name, for the timetable page. Public; at most 10. */
	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<List<TravelViews.TrainBrief>>> findTrains(String q) {
		String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
		if (query.length() < 2) {
			return reply(HttpStatus.OK, List.of(), "Type at least 2 characters");
		}
		if (query.length() > 60) {
			query = query.substring(0, 60);
		}
		List<TravelViews.TrainBrief> out = trainRepository.search(query, PageRequest.of(0, 10)).stream()
				.map(t -> new TravelViews.TrainBrief(t.getTrainNumber(), t.getTrainName(), t.getSourceStation(),
						t.getDestinationStation()))
				.toList();
		return reply(HttpStatus.OK, out, out.size() + " trains");
	}

	/* ------------------------------------------------------------ e-ticket codes */

	/** Signed code for the QR on the passenger's e-ticket (valid until 2 days after the journey) */
	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<String>> ticketCode(String token, long pnr) {
		String[] who = jwtUtil.getSubject(token);
		if (who == null || !"customer".equals(who[1])) {
			return reply(HttpStatus.UNAUTHORIZED, null, "Your session has ended. Log in again.");
		}
		Customer c = customerRepository.findByEmail(who[0]);
		Booking b = bookingRepository.findById(pnr).orElse(null);
		if (c == null || b == null || b.getCustomer().getId() != c.getId()) {
			return reply(HttpStatus.NOT_FOUND, null, "No booking with PNR " + pnr + ".");
		}
		LocalDateTime until = (b.getBoardingDate() != null ? b.getBoardingDate() : b.getJourneyDate()).plusDays(3)
				.atStartOfDay();
		String code = jwtUtil.signTicket(pnr, Date.from(until.atZone(IST).toInstant()));
		return reply(HttpStatus.OK, code, "E-ticket code");
	}

	/** Public: what a ticket checker sees after scanning the QR code */
	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<TravelViews.TicketCheck>> verifyTicket(String code) {
		Long pnr = jwtUtil.verifyTicket(code);
		if (pnr == null) {
			return reply(HttpStatus.OK, new TravelViews.TicketCheck(false,
					"This code isn't valid: it wasn't issued by Railway, was changed, or has expired.", null, null, null,
					null, null, null, null, null, null, List.of()), "Not valid");
		}
		Booking b = bookingRepository.findById(pnr).orElse(null);
		if (b == null) {
			return reply(HttpStatus.OK, new TravelViews.TicketCheck(false, "No booking with PNR " + pnr + ".", pnr, null,
					null, null, null, null, null, null, null, List.of()), "Not valid");
		}
		Quota quota = Quota.parse(b.getQuota());
		List<TravelViews.TicketCheckPassenger> rows = new ArrayList<>();
		int n = 1;
		for (Passenger p : b.getPassengers()) {
			rows.add(new TravelViews.TicketCheckPassenger(n++, p.getName(), p.getAge(), p.getGender(),
					TicketMailer.currentLabel(p, quota)));
		}
		Train train = trainRepository.findByTrainNumber(b.getTrainNumber());
		String departs = null;
		if (train != null && train.getStations() != null && b.getFromStop() < train.getStations().size()) {
			departs = Timelines.of(train).departs(b.getJourneyDate(), b.getFromStop()).format(WHEN);
		}
		boolean travels = rows.stream().anyMatch(r -> r.status().startsWith("CNF") || r.status().startsWith("RAC")
				|| r.status().contains("no berth"));
		Seat_type type = Seat_type.valueOf(b.getSeatType());
		return reply(HttpStatus.OK, new TravelViews.TicketCheck(travels,
				travels ? "Valid e-ticket" : "This ticket has no passenger who can travel (cancelled or waitlisted).", pnr,
				b.getTrainNumber(), b.getTrainName(), b.getFromStation(), b.getToStation(),
				(b.getBoardingDate() != null ? b.getBoardingDate() : b.getJourneyDate()).toString(), departs,
				CoachLayout.shortCode(type) + ", " + quota.label, b.getStatus(), rows), travels ? "Valid" : "Not valid");
	}

	private static String time(long minutes) {
		return LocalDateTime.of(2000, 1, 1, 0, 0).plusMinutes(minutes).format(TIME);
	}

	private static <T> ResponseEntity<ResponseStructure<T>> reply(HttpStatus status, T data, String message) {
		ResponseStructure<T> body = new ResponseStructure<>();
		body.setStatus(status.value());
		body.setMessage(message);
		body.setData2(data);
		return new ResponseEntity<>(body, status);
	}
}
