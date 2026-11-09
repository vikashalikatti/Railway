package com.project.railway.configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.project.railway.dto.ChartRun;
import com.project.railway.helper.TatkalRules;
import com.project.railway.repository.ChartRun_Repository;
import com.project.railway.repository.Train_Repository;
import com.project.railway.service.BookingEngine;

/**
 * Prepares reservation charts automatically. Every minute it looks at each train's runs for
 * yesterday, today and tomorrow:
 *  - 4 hours before the train leaves its first station: first chart (BookingEngine.prepareFirstChart)
 *  - 30 minutes before: final chart, booking closes (BookingEngine.prepareFinalChart)
 * Each chart runs exactly once (the chart_run row is locked and its stage checked), so a restart,
 * a slow minute or a second server never prepares a chart twice.
 */
@Configuration
@EnableScheduling
public class ChartScheduler {

	private static final Logger log = LoggerFactory.getLogger(ChartScheduler.class);
	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	@Autowired
	private Train_Repository trainRepository;
	@Autowired
	private ChartRun_Repository chartRepository;
	@Autowired
	private BookingEngine engine;
	@Autowired
	private com.project.railway.service.VikalpService vikalp;

	@Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
	public void prepareDueCharts() {
		LocalDateTime now = LocalDateTime.now(IST);
		LocalDate today = now.toLocalDate();
		java.util.Map<String, Integer> stages = new java.util.HashMap<>();
		for (ChartRun r : chartRepository.findByJourneyDateBetween(today.minusDays(1), today.plusDays(1))) {
			stages.put(r.getTrainNumber() + "|" + r.getJourneyDate(), r.getStage());
		}
		// one light query: each train's first departure and running days (not every stop of every train)
		for (Object[] row : trainRepository.findStartTimes()) {
			int trainNumber = ((Number) row[0]).intValue();
			String[] days = (String[]) row[2];
			if (days == null) {
				continue;
			}
			LocalTime start = row[1] != null ? (LocalTime) row[1] : LocalTime.MIDNIGHT;
			for (LocalDate date : List.of(today.minusDays(1), today, today.plusDays(1))) {
				if (!runsOn(days, date)) {
					continue;
				}
				LocalDateTime departure = date.atTime(start);
				if (now.isBefore(TatkalRules.firstChartAt(departure)) || now.isAfter(departure.plusDays(1))) {
					continue;
				}
				try {
					int stage = stages.getOrDefault(trainNumber + "|" + date, ChartRun.NOT_PREPARED);
					if (stage >= ChartRun.FINAL) {
						continue;
					}
					if (stage < ChartRun.FIRST && engine.prepareFirstChart(trainNumber, date)) {
						log.info("First chart prepared for train {} on {}", trainNumber, date);
						vikalp.process(trainNumber, date); // waitlisted Vikalp bookings: try another train
						stage = ChartRun.FIRST;
					}
					if (stage < ChartRun.FINAL && !now.isBefore(TatkalRules.finalChartAt(departure))
							&& engine.prepareFinalChart(trainNumber, date)) {
						log.info("Final chart prepared for train {} on {}", trainNumber, date);
					}
				} catch (RuntimeException e) {
					// try again next minute; one train's problem never stops the others' charts
					log.warn("Chart for train {} on {} failed, will retry: {}", trainNumber, date,
							e.toString());
				}
			}
		}
	}

	private static boolean runsOn(String[] days, LocalDate date) {
		String day = date.getDayOfWeek().name();
		return Arrays.stream(days).anyMatch(d -> d != null && d.trim().equalsIgnoreCase(day));
	}
}
