package com.project.railway.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.railway.dto.Booking;
import com.project.railway.dto.Customer;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.repository.Book_Repository;
import com.project.railway.repository.Customer_Repository;

/** Admin: find customers, see their bookings, block or unblock them */
@Service
public class CustomerAdminService {

	@Autowired
	private Customer_Repository customerRepository;
	@Autowired
	private Book_Repository bookingRepository;
	@Autowired
	private JwtUtil jwtUtil;
	@Autowired
	private AuditService audit;

	/** What the admin sees about a customer (never the password, OTP or photo) */
	public record CustomerView(int id, String name, String email, long mobile, String gender, int age, boolean verified,
			boolean blocked, String blockedReason, long bookings) {
	}

	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<List<CustomerView>>> list(String token, String q, int limit) {
		if (!isAdmin(token)) {
			return reply(HttpStatus.UNAUTHORIZED, null, "Invalid or Expired Token, Please Login Again");
		}
		String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
		List<CustomerView> rows = customerRepository
				.search(query.length() > 60 ? query.substring(0, 60) : query, PageRequest.of(0, Math.max(1, Math.min(limit, 200))))
				.stream().map(this::view).toList();
		return reply(HttpStatus.OK, rows, rows.size() + " customers");
	}

	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<List<Booking>>> bookings(String token, int id) {
		if (!isAdmin(token)) {
			return reply(HttpStatus.UNAUTHORIZED, null, "Invalid or Expired Token, Please Login Again");
		}
		Customer c = customerRepository.findById(id).orElse(null);
		if (c == null) {
			return reply(HttpStatus.NOT_FOUND, null, "No customer " + id + ".");
		}
		List<Booking> list = bookingRepository.findByCustomerOrderByBookedAtDesc(c);
		return reply(HttpStatus.OK, list, list.size() + " bookings");
	}

	/** Blocks: can't log in or book, and every log-in on every device ends now. Body: {"reason": "…"} */
	@Transactional
	public ResponseEntity<ResponseStructure<CustomerView>> block(String token, int id, Map<String, Object> body) {
		if (!isAdmin(token)) {
			return reply(HttpStatus.UNAUTHORIZED, null, "Invalid or Expired Token, Please Login Again");
		}
		Customer c = customerRepository.findById(id).orElse(null);
		if (c == null) {
			return reply(HttpStatus.NOT_FOUND, null, "No customer " + id + ".");
		}
		String reason = body == null || body.get("reason") == null ? "" : String.valueOf(body.get("reason")).trim();
		if (reason.length() > 200) {
			reason = reason.substring(0, 200);
		}
		c.setBlocked(true);
		c.setBlockedReason(reason.isEmpty() ? null : reason);
		c.setTokenVersion(JwtUtil.version(c.getTokenVersion()) + 1); // logs them out everywhere
		customerRepository.save(c);
		audit.record(token, "CUSTOMER_BLOCKED", "Customer " + id, c.getEmail() + (reason.isEmpty() ? "" : ": " + reason));
		return reply(HttpStatus.OK, view(c), c.getEmail() + " is blocked and logged out everywhere.");
	}

	@Transactional
	public ResponseEntity<ResponseStructure<CustomerView>> unblock(String token, int id) {
		if (!isAdmin(token)) {
			return reply(HttpStatus.UNAUTHORIZED, null, "Invalid or Expired Token, Please Login Again");
		}
		Customer c = customerRepository.findById(id).orElse(null);
		if (c == null) {
			return reply(HttpStatus.NOT_FOUND, null, "No customer " + id + ".");
		}
		c.setBlocked(false);
		c.setBlockedReason(null);
		customerRepository.save(c);
		audit.record(token, "CUSTOMER_UNBLOCKED", "Customer " + id, c.getEmail());
		return reply(HttpStatus.OK, view(c), c.getEmail() + " can log in again.");
	}

	private CustomerView view(Customer c) {
		return new CustomerView(c.getId(), c.getName(), c.getEmail(), c.getMobile(), c.getGender(), c.getAge(), c.isStatus(),
				Boolean.TRUE.equals(c.getBlocked()), c.getBlockedReason(), bookingRepository.countByCustomer(c));
	}

	private boolean isAdmin(String token) {
		String[] who = jwtUtil.getSubject(token);
		return who != null && "admin".equals(who[1]);
	}

	private static <T> ResponseEntity<ResponseStructure<T>> reply(HttpStatus status, T data, String message) {
		ResponseStructure<T> body = new ResponseStructure<>();
		body.setStatus(status.value());
		body.setMessage(message);
		body.setData2(data);
		return new ResponseEntity<>(body, status);
	}
}
