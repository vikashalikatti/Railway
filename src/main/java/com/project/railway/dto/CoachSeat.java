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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * One physical seat or berth in a coach (e.g. S3 seat 41, SIDE_LOWER).
 *
 * This row is what a booking locks with SELECT ... FOR UPDATE: every transaction
 * that adds a SeatOccupancy for a seat must hold this row's lock first.
 */
@Entity
@Table(name = "coach_seat", uniqueConstraints = @UniqueConstraint(name = "uk_coach_seat_number", columnNames = {
		"coach_id", "seat_number" }))
@Getter
@Setter
public class CoachSeat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "coach_id", nullable = false)
	private Coach coach;

	@Column(name = "seat_number", nullable = false)
	private int seatNumber;

	@Column(name = "berth_type", nullable = false, length = 20)
	private String berthType;
}
