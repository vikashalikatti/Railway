package com.project.railway.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.railway.dto.Station;
import com.project.railway.dto.StationMaster;
import com.project.railway.repository.StationMaster_Repository;
import com.project.railway.repository.Station_Repository;

/**
 * Station lookups for live search and train search.
 *
 * What a passenger types can be:
 *  - a station code      "SBC"       -> just that station
 *  - a city              "Bengaluru" -> every station in the city (SBC, YPR, SMVB…)
 *  - a station name      "KSR Bengaluru City Jn"
 *  - an old stop name    "Hubballi"  -> stops typed before codes existed
 */
@Service
public class StationDirectory {

	public static final int MAX_SUGGESTIONS = 10;

	/** One row in the live-search dropdown. `value` is what the search should send. */
	public record Suggestion(String type, String value, String code, String name, String city, String label) {
	}

	/** Which stops count as "this place" for a search */
	public record Place(Set<String> codes, Set<String> names, String label) {
		public boolean matches(Station stop) {
			if (stop == null) {
				return false;
			}
			if (stop.getStationCode() != null && codes.contains(stop.getStationCode().toUpperCase(Locale.ROOT))) {
				return true;
			}
			return stop.getStationName() != null && names.contains(stop.getStationName().trim().toLowerCase(Locale.ROOT));
		}

		public boolean overlaps(Place other) {
			for (String c : codes) {
				if (other.codes.contains(c)) {
					return true;
				}
			}
			for (String n : names) {
				if (other.names.contains(n)) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Track is never a straight line. Straight-line distance x 1.25 lands close to the published
	 * rail distance for most routes (Bengaluru-Hubballi, Bengaluru-Chennai, Bengaluru-Mysuru).
	 */
	public static final double TRACK_FACTOR = 1.25;

	@Autowired
	private StationMaster_Repository masterRepository;
	@Autowired
	private Station_Repository stationRepository;

	@Transactional(readOnly = true)
	public List<Suggestion> suggest(String text) {
		String q = text == null ? "" : text.trim();
		List<Suggestion> out = new ArrayList<>();
		if (q.isEmpty()) {
			return out;
		}

		List<StationMaster> stations = masterRepository.search(q, PageRequest.of(0, MAX_SUGGESTIONS));

		// "All stations" in a city, when the text is the start of a city with more than one station
		Set<String> cities = new LinkedHashSet<>();
		for (StationMaster s : stations) {
			if (s.getCity().toLowerCase(Locale.ROOT).startsWith(q.toLowerCase(Locale.ROOT))) {
				cities.add(s.getCity());
			}
		}
		for (String city : cities) {
			int count = masterRepository.findByCityIgnoreCaseOrderByName(city).size();
			if (count > 1) {
				out.add(new Suggestion("CITY", city, null, null, city, city + " (all " + count + " stations)"));
			}
		}

		Set<String> seenNames = new HashSet<>();
		for (StationMaster s : stations) {
			seenNames.add(s.getName().toLowerCase(Locale.ROOT));
			out.add(new Suggestion("STATION", s.getCode(), s.getCode(), s.getName(), s.getCity(), s.getName()));
		}

		// Old stops without a code (only if they aren't already a station or city in the list)
		for (String name : stationRepository.searchUncodedNames(q, PageRequest.of(0, 5))) {
			String key = name.trim().toLowerCase(Locale.ROOT);
			boolean shown = seenNames.contains(key) || cities.stream().anyMatch(c -> c.equalsIgnoreCase(name.trim()));
			if (!shown) {
				out.add(new Suggestion("STOP", name.trim(), null, name.trim(), null, name.trim()));
			}
		}
		return out.size() > MAX_SUGGESTIONS ? out.subList(0, MAX_SUGGESTIONS) : out;
	}

	/** Works out which stops a typed code, city or name refers to */
	@Transactional(readOnly = true)
	public Place resolve(String text) {
		String q = text == null ? "" : text.trim();
		Set<String> codes = new HashSet<>();
		Set<String> names = new HashSet<>();
		if (q.isEmpty()) {
			return new Place(codes, names, q);
		}

		var byCode = masterRepository.findByCodeIgnoreCase(q);
		if (byCode.isPresent()) {
			StationMaster s = byCode.get();
			codes.add(s.getCode().toUpperCase(Locale.ROOT));
			names.add(s.getName().toLowerCase(Locale.ROOT));
			return new Place(codes, names, s.getName() + " (" + s.getCode() + ")");
		}

		List<StationMaster> inCity = masterRepository.findByCityIgnoreCaseOrderByName(q);
		if (!inCity.isEmpty()) {
			for (StationMaster s : inCity) {
				codes.add(s.getCode().toUpperCase(Locale.ROOT));
				names.add(s.getName().toLowerCase(Locale.ROOT));
			}
			names.add(q.toLowerCase(Locale.ROOT)); // old stops named after the city
			return new Place(codes, names, inCity.get(0).getCity());
		}

		names.add(q.toLowerCase(Locale.ROOT));
		masterRepository.findFirstByNameIgnoreCase(q).ifPresent(s -> codes.add(s.getCode().toUpperCase(Locale.ROOT)));
		return new Place(codes, names, q);
	}

	/** Fills in the code of a stop whose name exactly matches a directory station */
	public void linkCode(Station stop) {
		if (stop.getStationCode() != null && !stop.getStationCode().isBlank()) {
			masterRepository.findByCodeIgnoreCase(stop.getStationCode().trim()).ifPresentOrElse(s -> {
				stop.setStationCode(s.getCode());
				stop.setStationName(s.getName());
			}, () -> stop.setStationCode(null));
			return;
		}
		if (stop.getStationName() != null) {
			masterRepository.findFirstByNameIgnoreCase(stop.getStationName().trim()).ifPresent(s -> {
				stop.setStationCode(s.getCode());
				stop.setStationName(s.getName());
			});
		}
	}

	/** True when the code is unknown (used to reject stops with a made-up code) */
	@Transactional(readOnly = true)
	public boolean unknownCode(String code) {
		return code != null && !code.isBlank() && masterRepository.findByCodeIgnoreCase(code.trim()).isEmpty();
	}

	/**
	 * Estimated km for each leg of a route given as station codes.
	 * Element i is the km from stop i-1 to stop i; element 0, and any leg where a station is
	 * unknown or has no map position, is null.
	 */
	@Transactional(readOnly = true)
	public List<Integer> legKm(List<String> codes) {
		List<StationMaster> found = new ArrayList<>();
		for (String c : codes) {
			String code = c == null ? "" : c.trim().toUpperCase(Locale.ROOT);
			found.add(code.isEmpty() ? null : masterRepository.findById(code).orElse(null));
		}
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < found.size(); i++) {
			StationMaster a = i == 0 ? null : found.get(i - 1);
			StationMaster b = found.get(i);
			if (a == null || b == null || !a.hasPosition() || !b.hasPosition()) {
				out.add(null);
			} else {
				double km = straightLineKm(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude()) * TRACK_FACTOR;
				out.add((int) Math.max(1, Math.round(km)));
			}
		}
		return out;
	}

	/** Great-circle distance between two points (haversine) */
	static double straightLineKm(double lat1, double lon1, double lat2, double lon2) {
		double r = 6371.0;
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
	}
}
