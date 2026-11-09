package com.project.railway.dto;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Who did what in the admin console, and when */
@Entity
@Table(name = "audit_log", indexes = { @Index(name = "idx_audit_at", columnList = "at"),
		@Index(name = "idx_audit_target", columnList = "target") })
@Getter
@Setter
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private LocalDateTime at;

	/** Admin name (or "system") */
	@Column(nullable = false, length = 60)
	private String actor;

	/** e.g. TRAIN_ADDED, STOPS_SAVED, RUN_CANCELLED, CUSTOMER_BLOCKED */
	@Column(nullable = false, length = 40)
	private String action;

	/** e.g. "Train 12627", "Customer 14" */
	@Column(length = 80)
	private String target;

	@Column(length = 500)
	private String details;
}
