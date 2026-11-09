package com.project.railway.helper;

import java.util.ArrayList;
import java.util.List;

/**
 * RAC (Reservation Against Cancellation). Plain Java so it can be unit-tested.
 *
 * Classes with side-lower berths (SL, 3A, 3E, 2A) keep some of them as RAC berths. When the class
 * is full, General and Tatkal passengers get RAC before the waitlist: each RAC berth seats two RAC
 * passengers. A freed berth goes to RAC passengers first, then the waitlist moves up into RAC.
 * When the chart is prepared, RAC passengers who still have no berth share an RAC berth in pairs.
 */
public final class RacRules {

	private RacRules() {
	}

	/** Passengers per RAC berth */
	public static final int PER_BERTH = 2;

	public static boolean hasRac(Seat_type type) {
		return type == Seat_type.SLEEPER_CLASS || type == Seat_type.AC3_TIER || type == Seat_type.AC3_ECONOMY
				|| type == Seat_type.AC2_TIER;
	}

	/** RAC berths per coach when the admin hasn't chosen */
	public static int defaultBerthsPerCoach(Seat_type type) {
		switch (type) {
		case SLEEPER_CLASS:
			return 4;
		case AC3_TIER:
		case AC3_ECONOMY:
			return 2;
		case AC2_TIER:
			return 1;
		default:
			return 0;
		}
	}

	/** Side-lower berths in one coach (the most that can be RAC berths) */
	public static int maxBerthsPerCoach(Seat_type type) {
		if (!hasRac(type)) {
			return 0;
		}
		int n = 0;
		for (int seat = 1; seat <= CoachLayout.capacity(type); seat++) {
			if (CoachLayout.SIDE_LOWER.equals(CoachLayout.berthType(type, seat))) {
				n++;
			}
		}
		return n;
	}

	/** One RAC passenger's stretch of the route: stops [from, to) */
	public record Stretch(int from, int to) {
		boolean covers(int stop) {
			return from <= stop && stop < to;
		}

		boolean overlaps(Stretch o) {
			return from < o.to && to > o.from;
		}
	}

	/**
	 * How many more RAC passengers fit on [from, to): capacity minus the most RAC passengers already
	 * on the train at any stop of that stretch.
	 */
	public static int room(int capacity, List<Stretch> booked, int from, int to) {
		int most = 0;
		for (int stop = from; stop < to; stop++) {
			int here = 0;
			for (Stretch s : booked) {
				if (s.covers(stop)) {
					here++;
				}
			}
			most = Math.max(most, here);
		}
		return Math.max(0, capacity - most);
	}

	/** An RAC berth and who already shares it */
	public static final class Berth {
		public final long seatId;
		public final List<Stretch> sharing = new ArrayList<>();
		/** Booked as a whole berth by someone else for part of the route */
		public final List<Stretch> blocked = new ArrayList<>();

		public Berth(long seatId) {
			this.seatId = seatId;
		}

		/** Can one more RAC passenger share this berth on this stretch? */
		public boolean fits(Stretch s) {
			for (Stretch b : blocked) {
				if (b.overlaps(s)) {
					return false;
				}
			}
			for (int stop = s.from; stop < s.to; stop++) {
				int here = 0;
				for (Stretch x : sharing) {
					if (x.covers(stop)) {
						here++;
					}
				}
				if (here >= PER_BERTH) {
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * Places RAC passengers (in RAC-number order) on RAC berths for the chart. Each passenger goes on
	 * the first berth that has room for their whole stretch, preferring a berth that already has
	 * someone (so berths are filled in pairs). Returns the berth index per passenger, or -1.
	 */
	public static int[] place(List<Stretch> passengers, List<Berth> berths) {
		int[] out = new int[passengers.size()];
		for (int i = 0; i < passengers.size(); i++) {
			Stretch s = passengers.get(i);
			int chosen = -1;
			for (int pass = 0; pass < 2 && chosen < 0; pass++) {
				for (int b = 0; b < berths.size(); b++) {
					Berth berth = berths.get(b);
					boolean shared = !berth.sharing.isEmpty();
					if ((pass == 0) == shared && berth.fits(s)) {
						chosen = b;
						break;
					}
				}
			}
			out[i] = chosen;
			if (chosen >= 0) {
				berths.get(chosen).sharing.add(s);
			}
		}
		return out;
	}
}
