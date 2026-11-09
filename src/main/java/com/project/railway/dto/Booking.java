package com.project.railway.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A PNR: one booking for up to 6 passengers on one train, date, class and segment.
 *
 * Stored in a new table (pnr_booking) so it doesn't clash with columns of the old,
 * never-used "booking" table.
 */
@Entity
@Table(name = "pnr_booking", indexes = {
		@Index(name = "idx_booking_train_date_class", columnList = "train_number, journey_date, seat_type") })
@Getter
@Setter
public class Booking {

	public static final String CONFIRMED = "CONFIRMED";
	public static final String PARTIAL = "PARTIALLY_CONFIRMED";
	public static final String WAITLISTED = "WAITLISTED";
	/** Everyone travels, some on RAC */
	public static final String RAC = "RAC";
	public static final String CANCELLED = "CANCELLED";

	/** The PNR number */
	@Id
	@GeneratedValue(generator = "pnr")
	@SequenceGenerator(initialValue = 456789211, allocationSize = 1, sequenceName = "pnr", name = "pnr")
	private Long bookingId;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	@Column(name = "train_number", nullable = false)
	private int trainNumber;

	@Column(name = "train_name")
	private String trainName;

	@Column(name = "from_station", nullable = false)
	private String fromStation;

	@Column(name = "to_station", nullable = false)
	private String toStation;

	/** Position of the boarding station on the route (0 = first) */
	@Column(name = "from_stop", nullable = false)
	private int fromStop;

	/** Position of the destination station on the route */
	@Column(name = "to_stop", nullable = false)
	private int toStop;

	@Column(name = "journey_date", nullable = false)
	private LocalDate journeyDate;

	@Column(name = "seat_type", nullable = false, length = 20)
	private String seatType;

	/** GN, TQ, PT, LD, SS or PH (empty means General, for bookings made before quotas existed) */
	@Column(length = 2)
	private String quota;

	@Column(name = "distance_km")
	private double distanceKm;

	@Column(name = "total_fare")
	private double totalFare;

	/** CONFIRMED, PARTIALLY_CONFIRMED, WAITLISTED or CANCELLED */
	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "booked_at", nullable = false)
	private LocalDateTime bookedAt;

	@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@OrderBy("id ASC")
	private List<Passenger> passengers = new ArrayList<>();

	/** Money due back (train cancelled by the railway: the full fare of everyone still travelling) */
	@Column(name = "refund_amount")
	private Double refundAmount;

	/**
	 * The date the passenger boards. journeyDate is the date the train leaves its FIRST station (seats,
	 * charts and runs are per train run); they differ when boarding on Day 2 or later.
	 */
	@Column(name = "boarding_date")
	private java.time.LocalDate boardingDate;

	/** The test payment that paid for this booking */
	@Column(name = "payment_id", length = 40)
	private String paymentId;

	/** Vikalp: if still waitlisted at the chart, move to another train with seats */
	private Boolean vikalp;

	/** PNR this booking was moved from under Vikalp */
	@Column(name = "vikalp_from")
	private Long vikalpFrom;

	/** A note for the passenger, e.g. "Moved from PNR 456789301 under Vikalp" */
	@Column(length = 200)
	private String note;

	/** NOT_PREPARED, PREPARED (first chart) or FINAL (filled in when a PNR is read; not stored) */
	@jakarta.persistence.Transient
	private String chartStatus;

	/** Works out the overall status from the passengers' statuses */
	public void refreshStatus() {
		// passengers without a berth (NB) follow the rest of the booking and don't decide its status
		long cnf = passengers.stream().filter(p -> Passenger.CONFIRMED.equals(p.getStatus())).count();
		long rac = passengers.stream().filter(p -> Passenger.RAC.equals(p.getStatus())).count();
		long wl = passengers.stream().filter(p -> Passenger.WAITLISTED.equals(p.getStatus())).count();
		if (cnf == 0 && rac == 0 && wl == 0) {
			status = CANCELLED;
		} else if (wl == 0 && rac == 0) {
			status = CONFIRMED;
		} else if (wl == 0) {
			status = RAC; // everyone travels, some sharing a berth
		} else if (cnf == 0 && rac == 0) {
			status = WAITLISTED;
		} else {
			status = PARTIAL;
		}
	}
}
