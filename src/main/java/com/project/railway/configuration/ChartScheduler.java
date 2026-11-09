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
import com.project.railway.dto.Station;
import com.project.railway.dto.Train;
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

	@Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
	public void prepareDueCharts() {
		LocalDateTime now = LocalDateTime.now(IST);
		LocalDate today = now.toLocalDate();
		for (Train train : trainRepository.findAll()) {
			List<Station> stops = train.getStations();
			if (stops == null || stops.isEmpty() || train.getSchedule() == null
					|| train.getSchedule().getRunningWeeks() == null) {
				continue;
			}
			LocalTime start = stops.get(0).getDepartureTime() != null ? stops.get(0).getDepartureTime()
					: LocalTime.MIDNIGHT;
			for (LocalDate date : List.of(today.minusDays(1), today, today.plusDays(1))) {
				if (!runsOn(train, date)) {
					continue;
				}
				LocalDateTime departure = date.atTime(start);
				if (now.isBefore(TatkalRules.firstChartAt(departure)) || now.isAfter(departure.plusDays(1))) {
					continue;
				}
				try {
					ChartRun run = chartRepository.findByTrainNumberAndJourneyDate(train.getTrainNumber(), date);
					int stage = run == null ? ChartRun.NOT_PREPARED : run.getStage();
					if (stage < ChartRun.FIRST && engine.prepareFirstChart(train.getTrainNumber(), date)) {
						log.info("First chart prepared for train {} on {}", train.getTrainNumber(), date);
						stage = ChartRun.FIRST;
					}
					if (stage < ChartRun.FINAL && !now.isBefore(TatkalRules.finalChartAt(departure))
							&& engine.prepareFinalChart(train.getTrainNumber(), date)) {
						log.info("Final chart prepared for train {} on {}", train.getTrainNumber(), date);
					}
				} catch (RuntimeException e) {
					// try again next minute; one train's problem never stops the others' charts
					log.warn("Chart for train {} on {} failed, will retry: {}", train.getTrainNumber(), date,
							e.toString());
				}
			}
		}
	}

	private static boolean runsOn(Train train, LocalDate date) {
		String day = date.getDayOfWeek().name();
		return Arrays.stream(train.getSchedule().getRunningWeeks()).anyMatch(d -> d != null && d.trim().equalsIgnoreCase(day));
	}
}
