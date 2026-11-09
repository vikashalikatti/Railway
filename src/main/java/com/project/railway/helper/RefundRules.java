package com.project.railway.helper;

/**
 * Cancellation charges and refunds, modelled on Indian Railways' refund rules. Plain Java so it can
 * be unit-tested.
 *
 * Confirmed ticket, cancelled:
 *   more than 48 h before departure      flat charge per passenger (by class)
 *   48 h to 12 h before                  25% of the fare (at least the flat charge)
 *   12 h before until the chart          50% of the fare (at least the flat charge)
 *   after the chart is prepared          no refund
 * RAC or waitlisted, cancelled up to 30 minutes before departure: fare minus a clerkage charge.
 * Tatkal confirmed: no refund. Premium Tatkal: no refund.
 * Still waitlisted when the chart is prepared (dropped by the railway): fare minus clerkage.
 * Train cancelled by the railway: everything back.
 * Meals and bedroll are refunded in full when cancelled before the chart.
 */
public final class RefundRules {

	private RefundRules() {
	}

	public static final double CLERKAGE = 60;

	/** What a passenger held when cancelled */
	public enum Held {
		CONFIRMED, RAC, WAITLISTED
	}

	/** One passenger's refund: fare and extras paid, the charge kept, and what goes back */
	public record Refund(double paid, double charge, double refund, String rule) {
	}

	/** Flat cancellation charge per passenger for a confirmed ticket */
	public static double flatCharge(Seat_type type) {
		switch (type) {
		case AC1_TIER:
		case EXECUTIVE_CHAIR_CAR:
			return 240;
		case AC2_TIER:
			return 200;
		case AC3_TIER:
		case AC3_ECONOMY:
		case AC_CHAIR_CAR:
			return 180;
		case SLEEPER_CLASS:
			return 120;
		default:
			return 60;
		}
	}

	/**
	 * The passenger cancels.
	 *
	 * @param minutesBefore minutes until the train leaves the passenger's boarding station (negative: it has left)
	 * @param chartPrepared the first chart has been prepared
	 */
	public static Refund onCancel(Held held, Quota quota, Seat_type type, double fare, double extras,
			long minutesBefore, boolean chartPrepared) {
		double paid = fare + extras;
		double extrasBack = chartPrepared ? 0 : extras;
		if (minutesBefore < 30) {
			return new Refund(paid, paid, 0, "Cancelled less than 30 minutes before departure: no refund");
		}
		if (held != Held.CONFIRMED) {
			if (quota == Quota.PT) {
				return new Refund(paid, paid, 0, "Premium Tatkal: no refund");
			}
			double charge = Math.min(fare, CLERKAGE);
			return back(paid, fare - charge + extrasBack, held == Held.RAC ? "RAC: clerkage charge" : "Waitlisted: clerkage charge");
		}
		if (quota == Quota.TQ || quota == Quota.PT) {
			return back(paid, extrasBack, quota.label + " confirmed: no refund of the fare");
		}
		if (chartPrepared) {
			return back(paid, 0, "Chart prepared: no refund for confirmed tickets");
		}
		double flat = Math.min(fare, flatCharge(type));
		double hours = minutesBefore / 60.0;
		double charge;
		String rule;
		if (hours > 48) {
			charge = flat;
			rule = "More than 48 hours before departure: flat charge";
		} else if (hours > 12) {
			charge = Math.max(flat, fare * 0.25);
			rule = "48 to 12 hours before departure: 25% of the fare";
		} else {
			charge = Math.max(flat, fare * 0.50);
			rule = "Less than 12 hours before departure: 50% of the fare";
		}
		return back(paid, fare - Math.min(fare, charge) + extrasBack, rule);
	}

	/** Still waitlisted when the chart was prepared: the railway drops the ticket */
	public static Refund droppedAtChart(double fare, double extras) {
		return back(fare + extras, fare - Math.min(fare, CLERKAGE) + extras, "Not confirmed at charting: clerkage charge");
	}

	/** The railway cancelled the train */
	public static Refund trainCancelled(double fare, double extras) {
		return back(fare + extras, fare + extras, "Train cancelled: full refund");
	}

	private static Refund back(double paid, double refund, String rule) {
		double r = Math.max(0, Math.round(refund));
		return new Refund(paid, Math.max(0, Math.round(paid - r)), r, rule);
	}
}
