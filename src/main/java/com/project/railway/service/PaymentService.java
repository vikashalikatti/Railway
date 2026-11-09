package com.project.railway.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.railway.dto.Booking;
import com.project.railway.dto.Customer;
import com.project.railway.dto.Payment;
import com.project.railway.dto.RefundEntry;
import com.project.railway.helper.BookingException;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.PaymentRules;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.repository.Customer_Repository;
import com.project.railway.repository.Payment_Repository;
import com.project.railway.repository.RefundEntry_Repository;

/**
 * TEST payment gateway: lets the app go through a real-looking payment step without moving money.
 *
 *   1. start   amount + card / UPI id / bank   -> PENDING (or FAILED for the test failure cases)
 *   2. confirm OTP 123456 (card) or approve on the "bank / UPI app" page -> PAID
 *   3. the booking uses the PAID payment once (USED); cancellations refund part of it
 * Payments that are paid but never used for a booking are refunded automatically after 30 minutes.
 * Card numbers and CVVs are never stored or logged: only the brand and the last 4 digits.
 */
@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	public static final int PENDING_MINUTES = 15;
	public static final int UNUSED_REFUND_MINUTES = 30;
	public static final int MAX_OTP_ATTEMPTS = 3;
	public static final List<String> BANKS = List.of("State Bank of India", "HDFC Bank", "ICICI Bank", "Axis Bank",
			"Canara Bank", "Bank of Baroda", "Kotak Mahindra Bank", "Punjab National Bank");

	@Autowired
	private Payment_Repository paymentRepository;
	@Autowired
	private RefundEntry_Repository refundRepository;
	@Autowired
	private Customer_Repository customerRepository;
	@Autowired
	private JwtUtil jwtUtil;

	/** What the app sends to start a payment (only the fields of the chosen method are used) */
	public record StartRequest(Double amount, String method, String cardNumber, String expiry, String cvv,
			String nameOnCard, String upiId, String bank) {
	}

	public record ConfirmRequest(String otp, Boolean approve) {
	}

	/** What the app sees; never contains card details */
	public record PaymentView(String id, double amount, String method, String instrument, String status,
			String failureReason, Long bookingId, double refunded, LocalDateTime createdAt, LocalDateTime paidAt,
			String nextStep) {
	}

	/* ------------------------------------------------------------------ endpoints */

	@Transactional
	public ResponseEntity<ResponseStructure<PaymentView>> start(String token, StartRequest in) {
		try {
			Customer c = customerFrom(token);
			if (in == null || in.amount() == null || in.amount() < 1 || in.amount() > 500000) {
				throw new BookingException(HttpStatus.BAD_REQUEST, "The amount must be between ₹1 and ₹5,00,000.");
			}
			String method = in.method() == null ? "" : in.method().trim().toUpperCase(Locale.ROOT);
			Payment p = new Payment();
			p.setId(newId());
			p.setCustomerId(c.getId());
			p.setAmount(Math.round(in.amount() * 100) / 100.0);
			p.setCreatedAt(LocalDateTime.now(IST));
			p.setMethod(method);
			p.setStatus(Payment.PENDING);
			String next;
			switch (method) {
			case "CARD": {
				String problem = PaymentRules.cardProblem(in.cardNumber(), in.expiry(), in.cvv(), YearMonth.now(IST));
				if (problem != null) {
					throw new BookingException(HttpStatus.BAD_REQUEST, problem);
				}
				p.setInstrument(PaymentRules.brand(in.cardNumber()) + " " + PaymentRules.mask(in.cardNumber()));
				if (PaymentRules.DECLINED_CARD.equals(PaymentRules.digits(in.cardNumber()))) {
					fail(p, "The bank declined this card (test card for a declined payment).");
				}
				next = "Enter the OTP sent to the phone linked to this card. Test OTP: " + PaymentRules.TEST_OTP;
				break;
			}
			case "UPI": {
				if (!PaymentRules.validUpi(in.upiId())) {
					throw new BookingException(HttpStatus.BAD_REQUEST, "Enter a UPI id like name@bank.");
				}
				String vpa = in.upiId().trim().toLowerCase(Locale.ROOT);
				p.setInstrument(vpa);
				if (PaymentRules.FAILING_UPI.equals(vpa)) {
					fail(p, "The payment was declined in the UPI app (test id for a declined payment).");
				}
				next = "Approve the request in your UPI app.";
				break;
			}
			case "NETBANKING": {
				if (in.bank() == null || !BANKS.contains(in.bank().trim())) {
					throw new BookingException(HttpStatus.BAD_REQUEST, "Choose your bank.");
				}
				p.setInstrument(in.bank().trim());
				next = "Log in on your bank's page and approve the payment.";
				break;
			}
			default:
				throw new BookingException(HttpStatus.BAD_REQUEST, "Choose card, UPI or net banking.");
			}
			paymentRepository.save(p);
			HttpStatus status = Payment.FAILED.equals(p.getStatus()) ? HttpStatus.PAYMENT_REQUIRED : HttpStatus.CREATED;
			return reply(status, view(p, Payment.FAILED.equals(p.getStatus()) ? null : next),
					Payment.FAILED.equals(p.getStatus()) ? p.getFailureReason() : next);
		} catch (BookingException e) {
			return reply(e.getStatus(), null, e.getMessage());
		}
	}

	@Transactional
	public ResponseEntity<ResponseStructure<PaymentView>> confirm(String token, String id, ConfirmRequest in) {
		try {
			Customer c = customerFrom(token);
			Payment p = id == null ? null : paymentRepository.lock(id);
			if (p == null || p.getCustomerId() != c.getId()) {
				throw new BookingException(HttpStatus.NOT_FOUND, "That payment doesn't exist.");
			}
			if (!Payment.PENDING.equals(p.getStatus())) {
				return reply(HttpStatus.OK, view(p, null), "This payment is already " + p.getStatus().toLowerCase(Locale.ROOT) + ".");
			}
			if (p.getCreatedAt().isBefore(LocalDateTime.now(IST).minusMinutes(PENDING_MINUTES))) {
				fail(p, "The payment timed out. Start again.");
			} else if ("CARD".equals(p.getMethod())) {
				String otp = in == null || in.otp() == null ? "" : in.otp().trim();
				if (PaymentRules.TEST_OTP.equals(otp)) {
					paid(p);
				} else {
					p.setOtpAttempts(p.getOtpAttempts() + 1);
					if (p.getOtpAttempts() >= MAX_OTP_ATTEMPTS) {
						fail(p, "Wrong OTP entered 3 times. The payment was cancelled.");
					} else {
						paymentRepository.save(p);
						return reply(HttpStatus.BAD_REQUEST, view(p, "Enter the OTP again."),
								"Wrong OTP. " + (MAX_OTP_ATTEMPTS - p.getOtpAttempts()) + " tries left.");
					}
				}
			} else if (in != null && Boolean.TRUE.equals(in.approve())) {
				paid(p);
			} else {
				fail(p, "You declined the payment.");
			}
			paymentRepository.save(p);
			boolean ok = Payment.PAID.equals(p.getStatus());
			return reply(ok ? HttpStatus.OK : HttpStatus.PAYMENT_REQUIRED, view(p, null),
					ok ? "Payment successful" : p.getFailureReason());
		} catch (BookingException e) {
			return reply(e.getStatus(), null, e.getMessage());
		}
	}

	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<List<PaymentView>>> mine(String token) {
		try {
			Customer c = customerFrom(token);
			List<PaymentView> out = paymentRepository.findByCustomerIdOrderByCreatedAtDesc(c.getId()).stream()
					.map(p -> view(p, null)).toList();
			return reply(HttpStatus.OK, out, out.size() + " payments");
		} catch (BookingException e) {
			return reply(e.getStatus(), null, e.getMessage());
		}
	}

	/* --------------------------------------------- used inside booking transactions */

	/** Locks a paid, unused payment of this customer for a booking (lock it before any seat) */
	public Payment lockForBooking(String id, int customerId) {
		Payment p = paymentRepository.lock(id);
		if (p == null || p.getCustomerId() != customerId) {
			throw new BookingException(HttpStatus.PAYMENT_REQUIRED, "That payment doesn't exist. Pay again.");
		}
		if (!Payment.PAID.equals(p.getStatus())) {
			throw new BookingException(HttpStatus.PAYMENT_REQUIRED, Payment.USED.equals(p.getStatus())
					? "This payment was already used for a booking. Pay again."
					: "This payment isn't complete. Pay again.");
		}
		return p;
	}

	/** The booking is saved: the payment is used; anything paid above the fare goes back at once */
	public void markUsed(Payment p, Booking b) {
		p.setStatus(Payment.USED);
		p.setBookingId(b.getBookingId());
		b.setPaymentId(p.getId());
		double extra = Math.round((p.getAmount() - b.getTotalFare()) * 100) / 100.0;
		paymentRepository.save(p);
		if (extra >= 1) {
			refund(b, extra, "Paid more than the fare");
		}
	}

	/**
	 * Records money going back for a booking: on the booking, on its payment (if any) and as a refund
	 * entry. Call inside the transaction that cancelled the passengers.
	 */
	public void refund(Booking b, double amount, String reason) {
		if (amount <= 0) {
			return;
		}
		b.setRefundAmount(Math.round(((b.getRefundAmount() == null ? 0 : b.getRefundAmount()) + amount) * 100) / 100.0);
		if (b.getPaymentId() != null) {
			Payment p = paymentRepository.lock(b.getPaymentId());
			if (p != null) {
				double refunded = Math.min(p.getAmount(), p.getRefunded() + amount);
				p.setRefunded(Math.round(refunded * 100) / 100.0);
				if (p.getRefunded() >= p.getAmount() - 0.5) {
					p.setStatus(Payment.REFUNDED);
				}
				paymentRepository.save(p);
			}
		}
		RefundEntry r = new RefundEntry();
		r.setBookingId(b.getBookingId());
		r.setPaymentId(b.getPaymentId());
		r.setAmount(Math.round(amount * 100) / 100.0);
		r.setReason(reason.length() > 120 ? reason.substring(0, 120) : reason);
		r.setRefundedAt(LocalDateTime.now(IST));
		refundRepository.save(r);
	}

	/** A payment that couldn't be used (the booking failed before saving): everything back */
	@Transactional
	public void refundUnused(String paymentId, String reason) {
		Payment p = paymentRepository.lock(paymentId);
		if (p != null && Payment.PAID.equals(p.getStatus())) {
			p.setStatus(Payment.REFUNDED);
			p.setRefunded(p.getAmount());
			paymentRepository.save(p);
			RefundEntry r = new RefundEntry();
			r.setPaymentId(p.getId());
			r.setAmount(p.getAmount());
			r.setReason(reason);
			r.setRefundedAt(LocalDateTime.now(IST));
			refundRepository.save(r);
		}
	}

	/** Paid but never used for a booking: refunded after 30 minutes */
	@Scheduled(fixedDelay = 5 * 60_000, initialDelay = 60_000)
	@Transactional
	public void refundAbandoned() {
		List<Payment> old = paymentRepository.findByStatusAndPaidAtBefore(Payment.PAID,
				LocalDateTime.now(IST).minusMinutes(UNUSED_REFUND_MINUTES));
		for (Payment p : old) {
			refundUnused(p.getId(), "Payment not used for a booking");
		}
		if (!old.isEmpty()) {
			log.info("Refunded {} test payments that were never used for a booking", old.size());
		}
	}

	/* ------------------------------------------------------------------ helpers */

	private static void paid(Payment p) {
		p.setStatus(Payment.PAID);
		p.setPaidAt(LocalDateTime.now(IST));
	}

	private static void fail(Payment p, String reason) {
		p.setStatus(Payment.FAILED);
		p.setFailureReason(reason);
	}

	private static String newId() {
		StringBuilder sb = new StringBuilder("pay_");
		for (int i = 0; i < 14; i++) {
			sb.append(ID_CHARS.charAt(RANDOM.nextInt(ID_CHARS.length())));
		}
		return sb.toString();
	}

	static PaymentView view(Payment p, String next) {
		return new PaymentView(p.getId(), p.getAmount(), p.getMethod(), p.getInstrument(), p.getStatus(),
				p.getFailureReason(), p.getBookingId(), p.getRefunded(), p.getCreatedAt(), p.getPaidAt(), next);
	}

	private Customer customerFrom(String token) {
		String[] subject = jwtUtil.getSubject(token);
		if (subject == null) {
			throw new BookingException(HttpStatus.UNAUTHORIZED, "Your session has ended. Log in again.");
		}
		if (!"customer".equals(subject[1])) {
			throw new BookingException(HttpStatus.FORBIDDEN, "Only passengers can pay for tickets.");
		}
		Customer c = customerRepository.findByEmail(subject[0]);
		if (c == null) {
			throw new BookingException(HttpStatus.UNAUTHORIZED, "Your account wasn't found. Log in again.");
		}
		return c;
	}

	private static <T> ResponseEntity<ResponseStructure<T>> reply(HttpStatus status, T data, String message) {
		ResponseStructure<T> body = new ResponseStructure<>();
		body.setStatus(status.value());
		body.setMessage(message);
		body.setData2(data);
		return new ResponseEntity<>(body, status);
	}
}
