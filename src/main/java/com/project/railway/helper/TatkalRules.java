package com.project.railway.helper;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tatkal and chart timings and charges, modelled on Indian Railways' rules.
 * Plain Java so it can be unit-tested.
 */
public final class TatkalRules {

	private TatkalRules() {
	}

	/** First chart: seats are frozen, unsold quota seats go to the waitlist, the rest of the waitlist is cancelled */
	public static final int FIRST_CHART_HOURS_BEFORE = 4;
	/** Final chart: booking closes */
	public static final int FINAL_CHART_MINUTES_BEFORE = 30;

	/** AC classes open for Tatkal at 10:00 the day before; Sleeper and second sitting at 11:00 */
	public static boolean isAc(Seat_type type) {
		return type != Seat_type.SLEEPER_CLASS && type != Seat_type.SECOND_CLASS;
	}

	/** When Tatkal and Premium Tatkal booking opens for a journey on this date */
	public static LocalDateTime opensAt(LocalDate journeyDate, Seat_type type) {
		return journeyDate.minusDays(1).atTime(isAc(type) ? 10 : 11, 0);
	}

	/**
	 * Tatkal charge per passenger: a share of the fare, kept between a minimum and a maximum
	 * (10% for second sitting, 30% for every other class).
	 */
	public static double charge(Seat_type type, double fare) {
		double pct;
		double min;
		double max;
		switch (type) {
		case SECOND_CLASS:
			pct = 0.10; min = 10; max = 15;
			break;
		case SLEEPER_CLASS:
			pct = 0.30; min = 100; max = 200;
			break;
		case AC_CHAIR_CAR:
			pct = 0.30; min = 125; max = 225;
			break;
		case AC3_TIER:
		case AC3_ECONOMY:
			pct = 0.30; min = 300; max = 400;
			break;
		case AC2_TIER:
		case EXECUTIVE_CHAIR_CAR:
			pct = 0.30; min = 400; max = 500;
			break;
		default:
			return 0; // no Tatkal in AC First Class
		}
		return Math.round(Math.max(min, Math.min(max, fare * pct)));
	}

	/**
	 * Premium Tatkal price factor: 1.0 while no Premium Tatkal seat is sold, rising in step with
	 * the share sold, up to 2.0 for the last seats.
	 */
	public static double premiumFactor(long sold, long total) {
		if (total <= 0) {
			return 1.0;
		}
		double share = Math.max(0, Math.min(1, (double) sold / total));
		return 1.0 + Math.round(share * 10) / 10.0; // steps of 10%
	}

	/** Fare per passenger in a quota */
	public static double fare(Quota quota, Seat_type type, double fare, long premiumSold, long premiumTotal) {
		switch (quota) {
		case TQ:
			return fare + charge(type, fare);
		case PT:
			return Math.round((fare + charge(type, fare)) * premiumFactor(premiumSold, premiumTotal));
		default:
			return fare;
		}
	}

	public static LocalDateTime firstChartAt(LocalDateTime departure) {
		return departure.minusHours(FIRST_CHART_HOURS_BEFORE);
	}

	public static LocalDateTime finalChartAt(LocalDateTime departure) {
		return departure.minusMinutes(FINAL_CHART_MINUTES_BEFORE);
	}
}
