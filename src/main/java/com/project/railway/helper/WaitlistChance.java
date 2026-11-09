package com.project.railway.helper;

/**
 * How likely a waitlisted ticket is to be confirmed (or reach RAC) by the chart. Plain Java so it
 * can be unit-tested.
 *
 * With enough history for this train and class (at least 20 waitlisted passengers in recent runs),
 * the share of past waitlisted passengers with the same or a higher WL number who were confirmed
 * is used. Otherwise a rule of thumb: about 8% of a class's berths are cancelled before the chart,
 * and RAC places count too.
 */
public final class WaitlistChance {

	private WaitlistChance() {
	}

	public static final int MIN_HISTORY = 20;
	public static final double CANCELLED_SHARE = 0.08;

	public record Chance(int percent, String label) {
	}

	/**
	 * @param position     current waitlist position (1 = next)
	 * @param classSeats   berths of the class on that date
	 * @param racPlaces    RAC places still free (the waitlist moves into RAC before berths free up)
	 * @param history      past waitlisted passengers at or below this position, or 0
	 * @param confirmed    how many of those were confirmed (or travelled on RAC) by the chart
	 */
	public static Chance estimate(int position, long classSeats, long racPlaces, int history, int confirmed) {
		double p;
		if (history >= MIN_HISTORY) {
			p = (double) confirmed / history;
		} else {
			double expectedFree = Math.max(1, classSeats * CANCELLED_SHARE) + racPlaces;
			p = 1.0 - (position - 1) / expectedFree;
			p = Math.max(0.03, Math.min(0.95, p));
		}
		int pct = (int) Math.round(Math.max(0, Math.min(1, p)) * 100);
		String label = pct >= 70 ? "High" : pct >= 35 ? "Medium" : "Low";
		return new Chance(pct, label);
	}
}
