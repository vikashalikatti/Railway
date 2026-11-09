package com.project.railway.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/** One traveller on a PNR, with their seat (CNF) or waitlist number (WL) */
@Entity
@Table(name = "passenger")
@Getter
@Setter
public class Passenger {

	public static final String CONFIRMED = "CNF";
	public static final String WAITLISTED = "WL";
	/** Child or infant travelling without a berth, with the rest of the booking */
	public static final String NO_BERTH = "NB";
	public static final String CANCELLED_TRAIN = "TRAIN";
	/** cancelReason values */
	public static final String CANCELLED_BY_USER = "USER";
	public static final String CANCELLED_AT_CHART = "CHART";
	public static final String CANCELLED = "CAN";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@Column(nullable = false, length = 50)
	private String name;

	private int age;

	@Column(length = 10)
	private String gender;

	/** Berth the passenger asked for (LOWER, UPPER, WINDOW...), or null */
	@Column(name = "berth_preference", length = 20)
	private String berthPreference;

	/** CNF, WL or CAN */
	@Column(nullable = false, length = 3)
	private String status;

	/** Waitlist number given at booking time (WL 7 stays 7 even after others confirm) */
	@Column(name = "wl_number")
	private Integer wlNumber;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "seat_id")
	private CoachSeat seat;

	/** Copied from the seat so the ticket still shows it after cancellation */
	@Column(name = "coach_number", length = 10)
	private String coachNumber;

	@Column(name = "seat_number")
	private Integer seatNumber;

	@Column(name = "berth_type", length = 20)
	private String berthType;

	private double fare;

	/** Travels without a berth (infant, or a 5-11 child who chose no berth) */
	@Column(name = "no_berth")
	private Boolean noBerth;

	/** NONE, VEG, NON_VEG or JAIN */
	@Column(length = 10)
	private String meal;

	private Boolean bedroll;

	/** Meal and bedroll charges (fare is the ticket only) */
	@Column(name = "extras_fare")
	private Double extrasFare;

	/** Solo senior (60+) who asked for help boarding; matched with a volunteer helper on the same coach */
	@Column(name = "needs_assistance")
	private Boolean needsAssistance;

	/** Volunteered to help a senior traveller who asked for boarding assistance */
	private Boolean helper;

	/** The passenger this one is matched with (senior <-> helper), or null */
	@Column(name = "companion_id")
	private Long companionId;

	/** What the ticket shows about the match, e.g. "Your helper: Ravi, S1 / 36, boards at Tumakuru" */
	@Column(name = "companion_note", length = 200)
	private String companionNote;

	/** A solo woman seated in a bay where other women are booked */
	@Column(name = "near_women")
	private Boolean nearWomen;

	/** Why the passenger was cancelled: USER, or CHART when still waitlisted at charting */
	@Column(name = "cancel_reason", length = 10)
	private String cancelReason;

	/** Current position in the waitlist (filled in when a PNR is read; not stored) */
	@Transient
	private Integer currentWlPosition;
}
