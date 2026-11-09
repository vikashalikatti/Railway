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

/** One refund paid back to a test payment (cancellation, dropped at chart, train cancelled, unused payment) */
@Entity
@Table(name = "refund_entry", indexes = { @Index(name = "idx_refund_booking", columnList = "booking_id"),
		@Index(name = "idx_refund_payment", columnList = "payment_id") })
@Getter
@Setter
public class RefundEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "payment_id", length = 40)
	private String paymentId;

	@Column(name = "booking_id")
	private Long bookingId;

	private double amount;

	@Column(length = 120)
	private String reason;

	@Column(name = "refunded_at", nullable = false)
	private LocalDateTime refundedAt;
}
