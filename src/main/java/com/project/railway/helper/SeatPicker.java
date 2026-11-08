package com.project.railway.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides which of the locked free seats each passenger gets ("weights").
 *
 * Every (passenger, seat) pair gets a score, and passengers pick greedily, most
 * demanding first:
 *   +10  the seat is the berth type the passenger asked for
 *   +8   passenger is 60+ and the seat is a lower berth
 *   -6   passenger is 60+ and the seat is an upper berth
 *   +6   same coach as the group's first seat
 *   +4   same bay as the group's first seat, -1 per bay further away
 *
 * Plain Java (no Spring, no database) so it can be unit-tested.
 */
public final class SeatPicker {

	private SeatPicker() {
	}

	public static final int SENIOR_AGE = 60;

	/** A free seat the booking transaction has already locked */
	public record Seat(long id, String coachNumber, int seatNumber, String berthType, int bay) {
	}

	/** One passenger: index in the request, age and berth preference (may be null) */
	public record Traveller(int index, int age, String preference) {
		boolean isSenior() {
			return age >= SENIOR_AGE;
		}

		boolean hasPreference() {
			return preference != null && !preference.isBlank();
		}
	}

	/**
	 * Assigns seats to travellers. Returns traveller index -> seat. Travellers left
	 * without a seat (more travellers than seats) are not in the map.
	 *
	 * @param seats candidate seats, already sorted by coach then seat number
	 */
	public static Map<Integer, Seat> assign(List<Traveller> travellers, List<Seat> seats) {
		Map<Integer, Seat> result = new LinkedHashMap<>();
		if (travellers.isEmpty() || seats.isEmpty()) {
			return result;
		}

		// Seniors choose first, then people with a preference, then everyone else
		List<Traveller> order = new ArrayList<>(travellers);
		order.sort(Comparator.comparingInt((Traveller t) -> t.isSenior() ? 0 : t.hasPreference() ? 1 : 2)
				.thenComparingInt(Traveller::index));

		List<Seat> remaining = new ArrayList<>(seats);
		Seat anchor = null;

		for (Traveller t : order) {
			if (remaining.isEmpty()) {
				break;
			}
			Seat best = null;
			int bestScore = Integer.MIN_VALUE;
			for (Seat s : remaining) {
				int score = score(t, s, anchor);
				if (score > bestScore) { // strict ">" keeps the lowest coach/seat number on a tie
					best = s;
					bestScore = score;
				}
			}
			result.put(t.index(), best);
			remaining.remove(best);
			if (anchor == null) {
				anchor = best;
			}
		}

		// keep the request's passenger order in the result
		Map<Integer, Seat> ordered = new LinkedHashMap<>();
		travellers.forEach(t -> {
			if (result.containsKey(t.index())) {
				ordered.put(t.index(), result.get(t.index()));
			}
		});
		return ordered;
	}

	static int score(Traveller t, Seat s, Seat anchor) {
		int score = 0;
		if (t.hasPreference() && t.preference().equalsIgnoreCase(s.berthType())) {
			score += 10;
		}
		if (t.isSenior()) {
			if (CoachLayout.isLower(s.berthType())) {
				score += 8;
			} else if (CoachLayout.isUpper(s.berthType())) {
				score -= 6;
			}
		}
		if (anchor != null && anchor.coachNumber().equals(s.coachNumber())) {
			score += 6;
			int distance = Math.abs(anchor.bay() - s.bay());
			score += distance == 0 ? 4 : -distance;
		}
		return score;
	}

	/** Coach with the most free seats that fits the whole group; otherwise the emptiest one */
	public static <K> K pickCoach(Map<K, Integer> freeSeatsByCoach, int groupSize) {
		K fits = null;
		K emptiest = null;
		int fitsFree = -1;
		int emptiestFree = -1;
		for (Map.Entry<K, Integer> e : freeSeatsByCoach.entrySet()) {
			int free = e.getValue();
			if (free >= groupSize && free > fitsFree) {
				fits = e.getKey();
				fitsFree = free;
			}
			if (free > emptiestFree) {
				emptiest = e.getKey();
				emptiestFree = free;
			}
		}
		return fits != null ? fits : emptiest;
	}

	/** Small helper for tests and callers: counts seats per berth type */
	public static Map<String, Integer> countByBerth(List<Seat> seats) {
		Map<String, Integer> counts = new HashMap<>();
		seats.forEach(s -> counts.merge(s.berthType(), 1, Integer::sum));
		return counts;
	}
}
