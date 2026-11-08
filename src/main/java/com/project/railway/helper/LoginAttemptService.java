package com.project.railway.helper;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Slows down password and OTP guessing, and stops OTP emails being sent over and over.
 *
 *  - 5 wrong tries for one account within 15 minutes lock that account's log-in for 15 minutes.
 *  - 30 wrong tries from one IP address within 15 minutes lock that address for 15 minutes
 *    (someone trying many accounts).
 *  - An OTP email can be sent to the same address at most once a minute.
 *
 * Kept in memory: a restart clears it, which is fine for a single server.
 */
@Component
public class LoginAttemptService {

	public static final int MAX_PER_ACCOUNT = 5;
	public static final int MAX_PER_IP = 30;
	public static final Duration WINDOW = Duration.ofMinutes(15);
	public static final Duration LOCK = Duration.ofMinutes(15);
	public static final Duration EMAIL_GAP = Duration.ofSeconds(60);

	private record Entry(int failures, Instant first, Instant lockedUntil) {
	}

	private final Map<String, Entry> failures = new ConcurrentHashMap<>();
	private final Map<String, Instant> lastEmail = new ConcurrentHashMap<>();

	/** Seconds until this account (and the caller's IP) may try again; 0 when allowed */
	public long secondsLocked(String account) {
		return Math.max(remaining(key(account)), remaining("ip:" + clientIp()));
	}

	public void failed(String account) {
		record(key(account), MAX_PER_ACCOUNT);
		record("ip:" + clientIp(), MAX_PER_IP);
	}

	public void succeeded(String account) {
		failures.remove(key(account));
	}

	/**
	 * Seconds to wait before another OTP email may go to this address; 0 means send now
	 * (and the time is noted).
	 */
	public long emailCooldown(String email) {
		String k = key("email:" + email);
		Instant now = Instant.now();
		Instant last = lastEmail.get(k);
		if (last != null && last.plus(EMAIL_GAP).isAfter(now)) {
			return Math.max(1, Duration.between(now, last.plus(EMAIL_GAP)).getSeconds());
		}
		lastEmail.put(k, now);
		if (lastEmail.size() > 10_000) {
			lastEmail.values().removeIf(t -> t.plus(EMAIL_GAP).isBefore(now));
		}
		return 0;
	}

	/** "Too many attempts. Try again in 12 minutes." */
	public static String waitMessage(long seconds) {
		long minutes = (seconds + 59) / 60;
		return minutes <= 1 ? "Too many attempts. Try again in a minute."
				: "Too many attempts. Try again in " + minutes + " minutes.";
	}

	private void record(String k, int max) {
		Instant now = Instant.now();
		failures.compute(k, (key, e) -> {
			if (e == null || e.first().plus(WINDOW).isBefore(now)) {
				e = new Entry(0, now, null);
			}
			int n = e.failures() + 1;
			return new Entry(n, e.first(), n >= max ? now.plus(LOCK) : e.lockedUntil());
		});
		if (failures.size() > 10_000) {
			failures.values().removeIf(e -> e.first().plus(WINDOW).isBefore(now)
					&& (e.lockedUntil() == null || e.lockedUntil().isBefore(now)));
		}
	}

	private long remaining(String k) {
		Entry e = failures.get(k);
		if (e == null || e.lockedUntil() == null) {
			return 0;
		}
		long s = Duration.between(Instant.now(), e.lockedUntil()).getSeconds();
		return Math.max(s, 0);
	}

	private static String key(String account) {
		return account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
	}

	/** The caller's address as the server sees it (X-Forwarded-For is not trusted: it can be faked) */
	private static String clientIp() {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		if (attrs instanceof ServletRequestAttributes sra) {
			return sra.getRequest().getRemoteAddr();
		}
		return "unknown";
	}
}
