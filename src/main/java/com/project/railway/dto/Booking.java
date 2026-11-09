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

	/** GN, TQ, PT, LD or SS (empty means General, for bookings made before quotas existed) */
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

	/** NOT_PREPARED, PREPARED (first chart) or FINAL (filled in when a PNR is read; not stored) */
	@jakarta.persistence.Transient
	private String chartStatus;

	/** Works out the overall status from the passengers' statuses */
	public void refreshStatus() {
		long cnf = passengers.stream().filter(p -> Passenger.CONFIRMED.equals(p.getStatus())).count();
		long wl = passengers.stream().filter(p -> Passenger.WAITLISTED.equals(p.getStatus())).count();
		if (cnf == 0 && wl == 0) {
			status = CANCELLED;
		} else if (wl == 0) {
			status = CONFIRMED;
		} else if (cnf == 0) {
			status = WAITLISTED;
		} else {
			status = PARTIAL;
		}
	}
}
