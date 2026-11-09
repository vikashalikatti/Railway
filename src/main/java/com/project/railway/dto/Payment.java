package com.project.railway.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A payment through the TEST gateway (no real money). Card numbers are never stored: only the brand
 * and last 4 digits.
 */
@Entity
@Table(name = "payment", indexes = @Index(name = "idx_payment_customer", columnList = "customer_id"))
@Getter
@Setter
public class Payment {

	public static final String PENDING = "PENDING"; // waiting for OTP or bank approval
	public static final String PAID = "PAID";
	public static final String USED = "USED"; // paid for a booking
	public static final String FAILED = "FAILED";
	public static final String REFUNDED = "REFUNDED"; // fully refunded (unused, or everything cancelled)

	/** e.g. pay_7Q2K9X1M4A */
	@Id
	@Column(length = 40)
	private String id;

	@JsonIgnore
	@Column(name = "customer_id", nullable = false)
	private int customerId;

	@Column(nullable = false)
	private double amount;

	/** CARD, UPI or NETBANKING */
	@Column(nullable = false, length = 12)
	private String method;

	/** "Visa •••• 1111", "vikas@okaxis", "State Bank of India" */
	@Column(length = 60)
	private String instrument;

	@Column(nullable = false, length = 10)
	private String status;

	@Column(name = "failure_reason", length = 120)
	private String failureReason;

	@Column(name = "booking_id")
	private Long bookingId;

	/** Refunded so far */
	private double refunded;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	@JsonIgnore
	@Column(name = "otp_attempts")
	private int otpAttempts;
}
