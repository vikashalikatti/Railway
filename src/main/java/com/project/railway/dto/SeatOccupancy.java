package com.project.railway.dto;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * "Seat X is taken on this date from stop fromStop to stop toStop."
 *
 * Stops are positions on the train's route (0 = first station). Two bookings of the
 * same seat on the same date clash when fromA < toB AND toA > fromB, so a seat booked
 * stops 0->2 can be booked again for 2->5 (per-segment reuse).
 *
 * The unique keys are a safety net: two bookings of the same seat and date can't both
 * start (or both end) at the same stop. Overlaps with different start AND end stops are
 * prevented by locking the CoachSeat row, not by a key.
 */
@Entity
@Table(name = "seat_occupancy", uniqueConstraints = {
		@UniqueConstraint(name = "uk_occupancy_from", columnNames = { "seat_id", "journey_date", "from_stop" }),
		@UniqueConstraint(name = "uk_occupancy_to", columnNames = { "seat_id", "journey_date", "to_stop" }) }, indexes = {
				@Index(name = "idx_occupancy_train_date", columnList = "train_number, journey_date") })
@Getter
@Setter
public class SeatOccupancy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seat_id", nullable = false)
	private CoachSeat seat;

	@Column(name = "train_number", nullable = false)
	private int trainNumber;

	@Column(name = "journey_date", nullable = false)
	private LocalDate journeyDate;

	@Column(name = "from_stop", nullable = false)
	private int fromStop;

	@Column(name = "to_stop", nullable = false)
	private int toStop;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "passenger_id", nullable = false, unique = true)
	private Passenger passenger;
}
