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

/**
 * Chart preparation for one train on one date. The row is locked while a chart is prepared,
 * so each chart runs exactly once even if two servers or timers try at the same moment.
 */
@Entity
@Table(name = "chart_run", uniqueConstraints = @UniqueConstraint(name = "uk_chart_run", columnNames = {
		"train_number", "journey_date" }))
@Getter
@Setter
public class ChartRun {
	public static final int NOT_PREPARED = 0;
	public static final int FIRST = 1;
	public static final int FINAL = 2;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "train_number", nullable = false)
	private int trainNumber;
	@Column(name = "journey_date", nullable = false)
	private LocalDate journeyDate;
	/** 0 not prepared, 1 first chart prepared, 2 final chart prepared */
	@Column(nullable = false)
	private int stage;
	@Column(name = "first_prepared_at")
	private LocalDateTime firstPreparedAt;
	@Column(name = "final_prepared_at")
	private LocalDateTime finalPreparedAt;
}
