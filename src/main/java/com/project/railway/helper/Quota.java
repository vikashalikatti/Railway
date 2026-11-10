package com.project.railway.helper;

import java.util.List;
import java.util.Locale;

/**
 * Reservation quotas. Every seat of a class belongs to one quota (CoachSeat.quota) and is only
 * sold in that quota until the first chart is prepared; then unsold seats of every quota are
 * released to the waiting list and to General booking.
 *
 * Plain Java so the rules can be unit-tested.
 */
public enum Quota {

	/** General */
	GN("General", "WL", true, 6),
	/** Tatkal: opens the day before the journey, extra charge */
	TQ("Tatkal", "TQWL", true, 4),
	/** Premium Tatkal: like Tatkal, but the fare rises as seats sell, and there is no waitlist */
	PT("Premium Tatkal", "", false, 4),
	/** Ladies: women, and children under 12 travelling with them */
	LD("Ladies", "", false, 6),
	/** Senior citizen / lower berth: men 60+ and women 45+, lower berths only */
	SS("Senior citizen", "", false, 2),
	/** Divyangjan (persons with disability): one passenger with a concession card, and one escort; lower berths */
	PH("Divyangjan", "", false, 2),
	/**
	 * RAC berths: side-lower berths shared by two RAC passengers. Never booked directly: passengers
	 * get RAC when the class is full (General and Tatkal), and RAC passengers are placed on these
	 * berths when the chart is prepared.
	 */
	RC("RAC berth", "", false, 0);

	public final String label;
	/** Prefix of the waitlist number on a ticket ("WL 3", "TQWL 3"); empty if this quota has no waitlist */
	public final String wlPrefix;
	public final boolean hasWaitlist;
	public final int maxPassengers;

	Quota(String label, String wlPrefix, boolean hasWaitlist, int maxPassengers) {
		this.label = label;
		this.wlPrefix = wlPrefix;
		this.hasWaitlist = hasWaitlist;
		this.maxPassengers = maxPassengers;
	}

	/** A quota code from a request or the database; missing or blank means General */
	public static Quota parse(String code) {
		if (code == null || code.isBlank()) {
			return GN;
		}
		return Quota.valueOf(code.trim().toUpperCase(Locale.ROOT));
	}

	public boolean isTatkal() {
		return this == TQ || this == PT;
	}

	/** Can passengers choose this quota when booking? (RAC berths can't be booked directly) */
	public boolean bookable() {
		return this != RC;
	}

	/** Do passengers who find no confirmed seat in this quota get RAC (before the waitlist)? */
	public boolean hasRac() {
		return this == GN || this == TQ;
	}

	/** Does this class sell seats in this quota? */
	public boolean offeredIn(Seat_type type) {
		switch (this) {
		case RC:
			return RacRules.hasRac(type);
		case TQ:
		case PT:
			return type != Seat_type.AC1_TIER; // no Tatkal in AC First Class
		case SS:
			return CoachLayout.hasBerths(type); // lower berths only exist in sleeper classes
		default:
			return true;
		}
	}

	/**
	 * Key of this quota's waitlist counter row. General keeps the class name on its own
	 * ("SLEEPER_CLASS"), so counters created before quotas existed carry on.
	 */
	public String counterKey(Seat_type type) {
		return this == GN ? type.name() : name() + ":" + CoachLayout.shortCode(type);
	}

	/** One passenger as far as quota rules care. divyang: has a disability concession card. */
	public record Traveller(int age, String gender, boolean divyang) {
		public Traveller(int age, String gender) {
			this(age, gender, false);
		}
	}

	/** Why these passengers can't book in this quota, or null if they can */
	public String eligibility(List<Traveller> travellers) {
		if (this == RC) {
			// checked first: RC allows 0 passengers, which would otherwise give a confusing "up to 0" message
			return "RAC berths can't be booked directly. Book in General quota: RAC is given when the class is full.";
		}
		if (travellers.size() > maxPassengers) {
			return label + " allows up to " + maxPassengers + " passengers per booking.";
		}
		switch (this) {
		case LD: {
			boolean adultWoman = false;
			for (Traveller t : travellers) {
				boolean female = "FEMALE".equalsIgnoreCase(t.gender());
				if (!female && t.age() >= 12) {
					return "Ladies quota is for women, and children under 12 travelling with them.";
				}
				adultWoman |= female && t.age() >= 12;
			}
			return adultWoman ? null : "Ladies quota needs at least one woman aged 12 or over.";
		}
		case PH: {
			long divyang = travellers.stream().filter(Traveller::divyang).count();
			if (divyang == 0) {
				return "Divyangjan quota needs a passenger with a disability concession card.";
			}
			return null; // up to 2: the passenger with the card and an escort (or two card holders)
		}
		case SS:
			for (Traveller t : travellers) {
				boolean female = "FEMALE".equalsIgnoreCase(t.gender());
				if (female ? t.age() < 45 : t.age() < 60) {
					return "Senior citizen quota is for men aged 60+ and women aged 45+.";
				}
			}
			return null;
		default:
			return null;
		}
	}
}
