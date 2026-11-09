package com.project.railway.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.railway.dto.ChartRun;
import com.project.railway.dto.ChartView;
import com.project.railway.dto.CoachSeat;
import com.project.railway.dto.Passenger;
import com.project.railway.dto.Station;
import com.project.railway.dto.Train;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.Quota;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.helper.TatkalRules;
import com.project.railway.repository.ChartRun_Repository;
import com.project.railway.repository.CoachSeat_Repository;
import com.project.railway.repository.Passenger_Repository;
import com.project.railway.repository.SeatOccupancy_Repository;
import com.project.railway.repository.Train_Repository;

/**
 * Reservation charts ("chart & vacancy").
 *
 *  - Anyone: once the first chart is prepared, every coach's berths and the stretches each one is
 *    booked for, so vacant berths can be spotted. No names, ages or PNRs.
 *  - Admin: the same at any time, plus every passenger with name, age, gender, PNR and berth.
 */
@Service
public class ChartService {

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

	@Autowired
	private Train_Repository trainRepository;
	@Autowired
	private CoachSeat_Repository seatRepository;
	@Autowired
	private SeatOccupancy_Repository occupancyRepository;
	@Autowired
	private Passenger_Repository passengerRepository;
	@Autowired
	private ChartRun_Repository chartRepository;
	@Autowired
	private JwtUtil jwtUtil;

	/** For passengers: only after the first chart, never with personal details */
	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<ChartView>> publicChart(int trainNo, String dateText) {
		ResponseStructure<ChartView> structure = new ResponseStructure<>();
		try {
			Run run = run(trainNo, dateText);
			if (run.stage < ChartRun.FIRST) {
				return reply(structure, HttpStatus.CONFLICT, "The chart for this train isn't prepared yet. It will be ready on "
						+ run.chartAt.format(WHEN) + ", 4 hours before departure.");
			}
			structure.setData2(build(run, false));
			return reply(structure, HttpStatus.OK, "Chart");
		} catch (IllegalArgumentException e) {
			return reply(structure, HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	/** For the admin: at any time, with every passenger */
	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<ChartView>> adminChart(String token, int trainNo, String dateText) {
		ResponseStructure<ChartView> structure = new ResponseStructure<>();
		String[] who = jwtUtil.getSubject(token);
		if (who == null || !"admin".equals(who[1])) {
			return reply(structure, HttpStatus.UNAUTHORIZED, "Invalid or Expired Token, Please Login Again");
		}
		try {
			structure.setData2(build(run(trainNo, dateText), true));
			return reply(structure, HttpStatus.OK, "Chart");
		} catch (IllegalArgumentException e) {
			return reply(structure, HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	/* ------------------------------------------------------------------ */

	private record Run(Train train, LocalDate date, int stage, LocalDateTime chartAt, LocalDateTime preparedAt) {
	}

	private Run run(int trainNo, String dateText) {
		Train train = trainRepository.findByTrainNumber(trainNo);
		if (train == null) {
			throw new IllegalArgumentException("Train " + trainNo + " doesn't exist.");
		}
		LocalDate date;
		try {
			date = LocalDate.parse(dateText == null ? "" : dateText.trim());
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("Enter the date as yyyy-MM-dd, e.g. 2026-11-10.");
		}
		List<Station> stops = train.getStations();
		if (stops == null || stops.isEmpty()) {
			throw new IllegalArgumentException("This train has no stops set up yet.");
		}
		if (train.getSchedule() == null || train.getSchedule().getRunningWeeks() == null || Arrays
				.stream(train.getSchedule().getRunningWeeks()).noneMatch(d -> d != null && d.trim().equalsIgnoreCase(date.getDayOfWeek().name()))) {
			throw new IllegalArgumentException("Train " + trainNo + " doesn't run on " + date + ".");
		}
		LocalTime start = stops.get(0).getDepartureTime() != null ? stops.get(0).getDepartureTime() : LocalTime.MIDNIGHT;
		ChartRun run = chartRepository.findByTrainNumberAndJourneyDate(trainNo, date);
		return new Run(train, date, run == null ? ChartRun.NOT_PREPARED : run.getStage(),
				TatkalRules.firstChartAt(date.atTime(start)), run == null ? null : run.getFirstPreparedAt());
	}

	private ChartView build(Run run, boolean withPassengers) {
		Train train = run.train();
		Map<Long, List<ChartView.Segment>> booked = new HashMap<>();
		for (Object[] row : occupancyRepository.findSegments(train.getTrainNumber(), run.date())) {
			booked.computeIfAbsent(((Number) row[0]).longValue(), k -> new ArrayList<>())
					.add(new ChartView.Segment(((Number) row[1]).intValue(), ((Number) row[2]).intValue()));
		}

		// class -> coach -> berths, in the train's coach order
		Map<String, Map<String, List<ChartView.ChartBerth>>> byClass = new LinkedHashMap<>();
		Map<String, int[]> totals = new HashMap<>(); // class -> [berths, vacant for the whole route]
		for (CoachSeat s : seatRepository.findSeatsOfTrainOn(train.getTrainNumber(), run.date())) {
			String type = s.getCoach().getCoachType();
			List<ChartView.Segment> segs = booked.getOrDefault(s.getId(), List.of());
			byClass.computeIfAbsent(type, k -> new LinkedHashMap<>())
					.computeIfAbsent(s.getCoach().getCoachNumber(), k -> new ArrayList<>())
					.add(new ChartView.ChartBerth(s.getSeatNumber(), s.getBerthType(), Quota.parse(s.getQuota()).name(), segs));
			int[] t = totals.computeIfAbsent(type, k -> new int[2]);
			t[0]++;
			if (segs.isEmpty()) {
				t[1]++;
			}
		}
		List<ChartView.ChartClass> classes = new ArrayList<>();
		byClass.forEach((type, coaches) -> {
			List<ChartView.ChartCoach> list = new ArrayList<>();
			coaches.forEach((no, berths) -> list.add(new ChartView.ChartCoach(no, berths)));
			int[] t = totals.get(type);
			classes.add(new ChartView.ChartClass(type, t[0], t[1], list));
		});

		List<ChartView.ChartPassenger> passengers = null;
		if (withPassengers) {
			passengers = new ArrayList<>();
			for (Passenger p : passengerRepository.findForChart(train.getTrainNumber(), run.date())) {
				var b = p.getBooking();
				String status = Passenger.CONFIRMED.equals(p.getStatus()) ? "CNF"
						: Passenger.WAITLISTED.equals(p.getStatus())
								? Quota.parse(b.getQuota()).wlPrefix + " " + p.getWlNumber()
								: "Not confirmed at charting";
				passengers.add(new ChartView.ChartPassenger(b.getBookingId(), p.getName(), p.getAge(), p.getGender(),
						b.getSeatType(), p.getCoachNumber(), p.getSeatNumber(), p.getBerthType(), b.getFromStation(),
						b.getToStation(), Quota.parse(b.getQuota()).label, status, assistance(p)));
			}
		}

		List<String> stops = train.getStations().stream().map(Station::getStationName).toList();
		return new ChartView(train.getTrainNumber(), train.getTrainName(), run.date().toString(), run.stage(),
				run.preparedAt() == null ? null : run.preparedAt().format(WHEN), run.chartAt().format(WHEN), stops,
				classes, passengers);
	}

	private static <T> ResponseEntity<ResponseStructure<T>> reply(ResponseStructure<T> structure, HttpStatus status,
			String message) {
		structure.setMessage(message);
		structure.setStatus(status.value());
		return new ResponseEntity<>(structure, status);
	}

	/** For station staff: who asked for boarding help, who volunteered, and who they're matched with */
	private static String assistance(com.project.railway.dto.Passenger p) {
		boolean needs = Boolean.TRUE.equals(p.getNeedsAssistance());
		boolean helper = Boolean.TRUE.equals(p.getHelper());
		if (!needs && !helper) {
			return null;
		}
		String role = needs ? "Needs boarding help" : "Volunteer helper";
		return p.getCompanionNote() == null ? role + (needs ? ", no helper yet" : ", not matched")
				: role + ". " + p.getCompanionNote();
	}
}
