package com.project.railway.helper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides which seats of one class belong to which quota, from the admin's percentages.
 *
 *  - Senior citizen: lower berths, one coach after another, so they're spread across the train
 *  - Ladies: a block of seats at the start of the first coach (kept together, as on real trains)
 *  - Premium Tatkal, then Tatkal: taken from the last coaches backwards
 *  - everything left: General
 *
 * Plain Java so it can be unit-tested.
 */
public final class QuotaPlanner {

	private QuotaPlanner() {
	}

	/** One seat as the planner sees it, in coach-then-seat-number order */
	public record SeatRef(long id, int coachId, int seatNumber, String berthType) {
	}

	/**
	 * Percent of a class's seats per quota (General gets the rest), plus RAC berths per coach and the
	 * Divyangjan share.
	 */
	public record Shares(int tatkal, int premium, int ladies, int senior, int racBerthsPerCoach, int divyang) {

		/** Without RAC berths or a Divyangjan share (as before those existed) */
		public Shares(int tatkal, int premium, int ladies, int senior) {
			this(tatkal, premium, ladies, senior, 0, 0);
		}

		public int total() {
			return tatkal + premium + ladies + senior + divyang;
		}

		/** Sensible starting split per class */
		public static Shares defaults(Seat_type type) {
			int rac = RacRules.defaultBerthsPerCoach(type);
			int ph = CoachLayout.hasBerths(type) && type != Seat_type.AC1_TIER ? 2 : 0;
			if (type == Seat_type.AC1_TIER) {
				return new Shares(0, 0, 5, 10, rac, 0);
			}
			if (!CoachLayout.hasBerths(type)) {
				return new Shares(30, 10, 5, 0, rac, 0); // chair cars and second sitting: no lower-berth quotas
			}
			return new Shares(30, 10, 5, 8, rac, ph);
		}

		/** Problem with these numbers for this class, or null */
		public String problem(Seat_type type) {
			if (tatkal < 0 || premium < 0 || ladies < 0 || senior < 0 || racBerthsPerCoach < 0 || divyang < 0) {
				return "Quota shares can't be negative";
			}
			if (total() > 90) {
				return "Quotas can take at most 90% of a class; keep some seats for General";
			}
			if ((tatkal > 0 || premium > 0) && !Quota.TQ.offeredIn(type)) {
				return "There is no Tatkal in " + CoachLayout.shortCode(type);
			}
			if (senior > 0 && !Quota.SS.offeredIn(type)) {
				return "Senior citizen quota needs lower berths, which " + CoachLayout.shortCode(type) + " doesn't have";
			}
			if (racBerthsPerCoach > 0 && !RacRules.hasRac(type)) {
				return CoachLayout.shortCode(type) + " has no side-lower berths for RAC";
			}
			if (racBerthsPerCoach > RacRules.maxBerthsPerCoach(type)) {
				return "At most " + RacRules.maxBerthsPerCoach(type) + " RAC berths per " + CoachLayout.shortCode(type) + " coach";
			}
			return null;
		}
	}

	public static Map<Long, Quota> plan(List<SeatRef> seats, Shares shares) {
		Map<Long, Quota> out = new LinkedHashMap<>();
		seats.forEach(s -> out.put(s.id(), Quota.GN));
		int n = seats.size();
		if (n == 0) {
			return out;
		}
		// RAC: side-lower berths, the last ones of each coach
		if (shares.racBerthsPerCoach() > 0) {
			Map<Integer, List<SeatRef>> sideLowers = new LinkedHashMap<>();
			for (SeatRef s : seats) {
				if (CoachLayout.SIDE_LOWER.equals(s.berthType())) {
					sideLowers.computeIfAbsent(s.coachId(), k -> new ArrayList<>()).add(s);
				}
			}
			for (List<SeatRef> coach : sideLowers.values()) {
				for (int i = 0; i < Math.min(shares.racBerthsPerCoach(), coach.size()); i++) {
					out.put(coach.get(coach.size() - 1 - i).id(), Quota.RC);
				}
			}
		}

		// Divyangjan: lower berths (any seat in sitting classes), from the first coach, at least 2 when there's a share
		if (shares.divyang() > 0) {
			int left = Math.max(2, (n * shares.divyang() + 99) / 100);
			boolean berths = seats.stream().anyMatch(s -> CoachLayout.LOWER.equals(s.berthType()));
			for (SeatRef s : seats) {
				if (left == 0) {
					break;
				}
				if (out.get(s.id()) == Quota.GN && (!berths || CoachLayout.LOWER.equals(s.berthType()))) {
					out.put(s.id(), Quota.PH);
					left--;
				}
			}
		}

		Map<Quota, Integer> want = new EnumMap<>(Quota.class);
		want.put(Quota.SS, n * shares.senior() / 100);
		want.put(Quota.LD, n * shares.ladies() / 100);
		want.put(Quota.PT, n * shares.premium() / 100);
		want.put(Quota.TQ, n * shares.tatkal() / 100);

		// Senior citizen: lower berths, round-robin over coaches
		Map<Integer, List<SeatRef>> lowersByCoach = new LinkedHashMap<>();
		for (SeatRef s : seats) {
			if (CoachLayout.isLower(s.berthType()) && out.get(s.id()) == Quota.GN) {
				lowersByCoach.computeIfAbsent(s.coachId(), k -> new ArrayList<>()).add(s);
			}
		}
		int left = want.get(Quota.SS);
		Map<Integer, Integer> next = new HashMap<>();
		while (left > 0) {
			boolean took = false;
			for (Map.Entry<Integer, List<SeatRef>> e : lowersByCoach.entrySet()) {
				int i = next.getOrDefault(e.getKey(), 0);
				if (left > 0 && i < e.getValue().size()) {
					out.put(e.getValue().get(i).id(), Quota.SS);
					next.put(e.getKey(), i + 1);
					left--;
					took = true;
				}
			}
			if (!took) {
				break; // no more lower berths
			}
		}

		// Ladies: from the front
		left = want.get(Quota.LD);
		for (int i = 0; i < n && left > 0; i++) {
			if (out.get(seats.get(i).id()) == Quota.GN) {
				out.put(seats.get(i).id(), Quota.LD);
				left--;
			}
		}

		// Premium Tatkal then Tatkal: from the back
		for (Quota q : List.of(Quota.PT, Quota.TQ)) {
			left = want.get(q);
			for (int i = n - 1; i >= 0 && left > 0; i--) {
				if (out.get(seats.get(i).id()) == Quota.GN) {
					out.put(seats.get(i).id(), q);
					left--;
				}
			}
		}
		return out;
	}
}
