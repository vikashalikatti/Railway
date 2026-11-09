package com.project.railway.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.project.railway.dto.Booking;
import com.project.railway.dto.Station;
import com.project.railway.dto.Train;
import com.project.railway.helper.BookingException;
import com.project.railway.helper.StopDays;
import com.project.railway.repository.Book_Repository;
import com.project.railway.repository.Train_Repository;

/**
 * Vikalp (alternate train): when the chart is prepared, a fully waitlisted booking that opted in is
 * moved to another train between the same stations, in the same class, leaving within 12 hours after
 * the original, that still has a free berth for every passenger. Trains are tried in order of
 * departure. If none has room, the passengers are dropped with the usual refund.
 */
@Service
public class VikalpService {

	private static final Logger log = LoggerFactory.getLogger(VikalpService.class);
	public static final int WINDOW_HOURS = 12;

	@Autowired
	private BookingEngine engine;
	@Autowired
	private BookingService bookingService;
	@Autowired
	private Book_Repository bookingRepository;
	@Autowired
	private Train_Repository trainRepository;

	/** Called right after a train run's first chart */
	public void process(int trainNumber, LocalDate runDate) {
		for (Booking b : bookingRepository.findByTrainNumberAndJourneyDate(trainNumber, runDate)) {
			if (!BookingEngine.vikalpPending(b)) {
				continue;
			}
			Booking moved = null;
			try {
				moved = tryMove(b);
			} catch (RuntimeException e) {
				log.warn("Vikalp for PNR {} failed: {}", b.getBookingId(), e.toString());
			}
			if (moved != null) {
				log.info("Vikalp: PNR {} moved to PNR {} on train {}", b.getBookingId(), moved.getBookingId(),
						moved.getTrainNumber());
			} else {
				engine.dropVikalpLeftovers(b.getBookingId());
			}
		}
	}

	private Booking tryMove(Booking b) {
		Train original = trainRepository.findByTrainNumber(b.getTrainNumber());
		if (original == null || original.getStations() == null) {
			return null;
		}
		List<Station> stops = original.getStations();
		Station from = stops.get(b.getFromStop());
		Station to = stops.get(b.getToStop());
		LocalDateTime leaves = Timelines.of(original).departs(b.getJourneyDate(), b.getFromStop());

		Set<String> codes = new HashSet<>();
		Set<String> names = new HashSet<>();
		for (Station s : List.of(from, to)) {
			if (s.getStationCode() != null) {
				codes.add(s.getStationCode().toUpperCase(Locale.ROOT));
			}
			names.add(s.getStationName().trim().toLowerCase(Locale.ROOT));
		}
		Set<Integer> candidates = new HashSet<>(trainRepository.findNumbersStoppingAt(codes, names));
		candidates.remove(b.getTrainNumber());

		record Option(Train train, int fromStop, int toStop, LocalDateTime departs) {
		}
		List<Option> options = new ArrayList<>();
		for (Train t : trainRepository.findAllById(candidates)) {
			List<Station> ts = t.getStations();
			int f = indexOf(ts, from, 0);
			int e = f < 0 ? -1 : indexOf(ts, to, f + 1);
			if (f < 0 || e < 0) {
				continue;
			}
			StopDays.Timeline tl = Timelines.of(ts);
			// the run whose departure from the boarding station falls in the window
			for (LocalDate boarding : List.of(leaves.toLocalDate(), leaves.toLocalDate().plusDays(1))) {
				LocalDateTime d = tl.departs(tl.runDate(boarding, f), f);
				long minutes = Duration.between(leaves, d).toMinutes();
				if (minutes >= 0 && minutes <= WINDOW_HOURS * 60L) {
					options.add(new Option(t, f, e, d));
				}
			}
		}
		options.sort(Comparator.comparing(Option::departs));
		for (Option o : options) {
			JourneyPlan plan;
			try {
				plan = bookingService.plan(o.train().getTrainNumber(), b.getSeatType(), o.departs().toLocalDate().toString(),
						o.train().getStations().get(o.fromStop()).getStationName(),
						o.train().getStations().get(o.toStop()).getStationName(), "GN");
			} catch (BookingException ex) {
				continue; // doesn't run that day, no such class, cancelled…
			}
			if (plan.closedReason() != null && !plan.anyQuota()) {
				continue;
			}
			Booking moved = engine.moveForVikalp(b.getBookingId(), plan);
			if (moved != null) {
				return moved;
			}
		}
		return null;
	}

	/** Same station: same code when both have one, otherwise the same name */
	private static int indexOf(List<Station> stops, Station want, int startAt) {
		for (int i = startAt; i < stops.size(); i++) {
			Station s = stops.get(i);
			boolean same = s.getStationCode() != null && want.getStationCode() != null
					? s.getStationCode().equalsIgnoreCase(want.getStationCode())
					: s.getStationName() != null && s.getStationName().trim().equalsIgnoreCase(want.getStationName().trim());
			if (same) {
				return i;
			}
		}
		return -1;
	}
}
