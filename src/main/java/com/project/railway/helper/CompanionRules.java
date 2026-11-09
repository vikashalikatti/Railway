package com.project.railway.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Who should sit near whom, and senior / helper matching. Plain Java so it can be unit-tested.
 *
 * - A woman (12+) who is the only passenger with a berth on her ticket is placed, when possible,
 *   in a bay where other women (18+) are booked for an overlapping stretch. Nobody is ever shown
 *   where women sit; the seat picker just prefers those bays.
 * - A senior (60+) travelling alone can ask for boarding assistance. Adults (18-59) can volunteer
 *   as helpers. Each senior is matched with one helper in the same coach who is already on the
 *   train when the senior boards; helpers who stay until the senior gets off, and closer bays, win.
 */
public final class CompanionRules {

	private CompanionRules() {
	}

	public static final int SENIOR_AGE = SeatPicker.SENIOR_AGE;
	public static final int HELPER_MIN_AGE = 18;
	public static final int SOLO_WOMAN_MIN_AGE = 12;
	/** Women counted as company for a solo woman */
	public static final int WOMAN_COMPANY_MIN_AGE = 18;

	public static boolean isFemale(String gender) {
		return gender != null && gender.trim().toLowerCase(Locale.ROOT).equals("female");
	}

	public static boolean canAskForAssistance(int age, int passengersWithBerth) {
		return age >= SENIOR_AGE && passengersWithBerth == 1;
	}

	public static boolean canHelp(int age) {
		return age >= HELPER_MIN_AGE && age < SENIOR_AGE;
	}

	/** Near flags a passenger with a berth looks for */
	public static int seeks(int age, String gender, boolean needsAssistance, boolean helper, int passengersWithBerth) {
		int flags = 0;
		if (passengersWithBerth == 1 && age >= SOLO_WOMAN_MIN_AGE && isFemale(gender)) {
			flags |= SeatPicker.Near.WOMEN;
		}
		if (needsAssistance) {
			flags |= SeatPicker.Near.HELPER;
		}
		if (helper) {
			flags |= SeatPicker.Near.SENIOR_NEEDING_HELP;
		}
		return flags;
	}

	/** Someone already booked on an overlapping stretch */
	public record Neighbour(String coachNumber, int bay, String gender, int age, boolean needsAssistance, boolean helper,
			boolean matched) {
	}

	/** Bay key -> Near flags, from the people already booked */
	public static Map<String, Integer> nearFlags(List<Neighbour> neighbours) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Neighbour n : neighbours) {
			int f = 0;
			if (isFemale(n.gender()) && n.age() >= WOMAN_COMPANY_MIN_AGE) {
				f |= SeatPicker.Near.WOMEN;
			}
			if (n.helper() && !n.matched()) {
				f |= SeatPicker.Near.HELPER;
			}
			if (n.needsAssistance() && !n.matched()) {
				f |= SeatPicker.Near.SENIOR_NEEDING_HELP;
			}
			if (f != 0) {
				out.merge(SeatPicker.bayKey(n.coachNumber(), n.bay()), f, (a, b) -> a | b);
			}
		}
		return out;
	}

	/** A confirmed senior or helper, for matching */
	public record Candidate(long id, long bookingId, String coachNumber, int bay, int fromStop, int toStop) {
	}

	/** One senior with one helper */
	public record Match(Candidate senior, Candidate helper) {
	}

	/**
	 * Matches each senior (lowest id first) with one free helper: same coach, a different ticket,
	 * on the train when the senior boards (boards at or before, gets off after). Helpers who stay
	 * until the senior gets off come first, then the nearest bay, then the lowest id.
	 */
	public static List<Match> match(List<Candidate> seniors, List<Candidate> helpers) {
		List<Match> out = new ArrayList<>();
		Set<Long> used = new HashSet<>();
		List<Candidate> order = new ArrayList<>(seniors);
		order.sort(Comparator.comparingLong(Candidate::id));
		for (Candidate s : order) {
			Candidate best = null;
			for (Candidate h : helpers) {
				if (used.contains(h.id()) || h.bookingId() == s.bookingId() || !h.coachNumber().equals(s.coachNumber())
						|| h.fromStop() > s.fromStop() || h.toStop() <= s.fromStop()) {
					continue;
				}
				if (best == null || better(h, best, s)) {
					best = h;
				}
			}
			if (best != null) {
				used.add(best.id());
				out.add(new Match(s, best));
			}
		}
		return out;
	}

	private static boolean better(Candidate a, Candidate b, Candidate senior) {
		boolean aStays = a.toStop() >= senior.toStop();
		boolean bStays = b.toStop() >= senior.toStop();
		if (aStays != bStays) {
			return aStays;
		}
		int da = Math.abs(a.bay() - senior.bay());
		int db = Math.abs(b.bay() - senior.bay());
		if (da != db) {
			return da < db;
		}
		return a.id() < b.id();
	}

	/** "Ravi Kumar" -> "Ravi" */
	public static String firstName(String name) {
		String n = name == null ? "" : name.trim();
		int sp = n.indexOf(' ');
		return sp > 0 ? n.substring(0, sp) : n;
	}
}
