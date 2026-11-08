package com.project.railway.dto;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Last waitlist number handed out for one train, date and class.
 *
 * This row is locked (SELECT ... FOR UPDATE) whenever someone is put on, or promoted
 * from, the waitlist, so WL numbers are never duplicated and promotions happen in order.
 */
@Entity
@Table(name = "waitlist_counter", uniqueConstraints = @UniqueConstraint(name = "uk_waitlist_counter", columnNames = {
		"train_number", "journey_date", "seat_type" }))
@Getter
@Setter
public class WaitlistCounter {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "train_number", nullable = false)
	private int trainNumber;

	@Column(name = "journey_date", nullable = false)
	private LocalDate journeyDate;

	@Column(name = "seat_type", nullable = false, length = 20)
	private String seatType;

	@Column(name = "last_number", nullable = false)
	private int lastNumber;
}
