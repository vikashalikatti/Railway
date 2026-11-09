package com.project.railway.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** What happened to one train on one date: cancelled, rescheduled (delay) and where it is now */
@Entity
@Table(name = "train_run", uniqueConstraints = @UniqueConstraint(name = "uk_train_run", columnNames = {
		"train_number", "journey_date" }))
@Getter
@Setter
public class TrainRun {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "train_number", nullable = false)
	private int trainNumber;
	@Column(name = "journey_date", nullable = false)
	private LocalDate journeyDate;
	private boolean cancelled;
	@Column(name = "cancel_reason", length = 200)
	private String cancelReason;
	/** Minutes late (rescheduled departure, or running late) */
	@Column(name = "delay_minutes")
	private int delayMinutes;
	/** Position of the last station the train has reached or left (0 = first); null = not started */
	@Column(name = "current_stop")
	private Integer currentStop;
	/** Has left currentStop (true) or is standing at it (false) */
	private Boolean departed;
	@Column(length = 200)
	private String note;
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	/** Delay (minutes) passengers were last emailed about; they're emailed again when it grows by 30 */
	@Column(name = "late_notified_minutes")
	private Integer lateNotifiedMinutes;
}
