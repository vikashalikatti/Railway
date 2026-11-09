package com.project.railway.service;

import java.util.List;

import com.project.railway.dto.Station;
import com.project.railway.dto.Train;
import com.project.railway.helper.StopDays;

/** A train's stops as a StopDays timeline (day numbers worked out where the timetable gives none) */
public final class Timelines {

	private Timelines() {
	}

	public static StopDays.Timeline of(List<Station> stops) {
		return StopDays.timeline(stops.stream()
				.map(s -> new StopDays.Stop(s.getArrivalTime(), s.getDepartureTime(), s.getDay())).toList());
	}

	public static StopDays.Timeline of(Train train) {
		return of(train.getStations() == null ? List.of() : train.getStations());
	}
}
