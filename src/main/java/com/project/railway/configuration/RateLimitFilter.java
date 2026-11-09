package com.project.railway.configuration;

import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.project.railway.helper.RateLimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Requests per minute per client on public and sensitive endpoints, so nobody can try every PNR,
 * flood log-ins or hammer the payment page. Over the limit: 429 with a Retry-After header.
 *
 * The client is the connecting IP address. X-Forwarded-For is only trusted from this machine (the
 * Vite dev proxy adds it), because anyone else could put any address there.
 */
public class RateLimitFilter extends OncePerRequestFilter {

	private record Rule(String method, String prefix, RateLimiter limiter) {
	}

	private final Map<String, Rule> rules = new LinkedHashMap<>();

	public RateLimitFilter() {
		add("POST", "/customer/login", 20);
		add("POST", "/admin/login", 20);
		add("POST", "/customer/signup", 10);
		add("POST", "/customer/verify_otp", 20);
		add("POST", "/customer/resend_otp", 10);
		add("POST", "/customer/forgot", 10);
		add("POST", "/customer/reset-password", 10);
		add("GET", "/customer/pnr-status/", 20);
		add("GET", "/customer/verify-ticket", 30);
		add("POST", "/customer/payments", 30);
		add("GET", "/customer/chart/", 60);
		add("GET", "/customer/running/", 120);
		add("GET", "/customer/timetable/", 120);
		add("GET", "/customer/train-search", 120);
		add("GET", "/customer/stations", 240);
	}

	private void add(String method, String prefix, int perMinute) {
		rules.put(method + " " + prefix, new Rule(method, prefix, new RateLimiter(perMinute)));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
		String path = req.getRequestURI().substring(req.getContextPath().length());
		for (Rule r : rules.values()) {
			if (r.method().equals(req.getMethod()) && path.startsWith(r.prefix())) {
				if (!r.limiter().allow(r.prefix() + "|" + client(req), System.currentTimeMillis())) {
					res.setStatus(429);
					res.setHeader("Retry-After", String.valueOf(r.limiter().retryAfterSeconds()));
					res.setContentType(MediaType.APPLICATION_JSON_VALUE);
					res.setCharacterEncoding("UTF-8");
					res.getWriter().write("{\"status\":429,\"message\":\"Too many requests. Wait a minute and try again.\","
							+ "\"data\":null,\"data2\":null}");
					return;
				}
				break;
			}
		}
		chain.doFilter(req, res);
	}

	static String client(HttpServletRequest req) {
		String remote = req.getRemoteAddr();
		String forwarded = req.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank() && isLocal(remote)) {
			String first = forwarded.split(",")[0].trim();
			if (!first.isEmpty() && first.length() <= 45) {
				return first;
			}
		}
		return remote;
	}

	private static boolean isLocal(String ip) {
		try {
			return ip != null && InetAddress.getByName(ip).isLoopbackAddress();
		} catch (IOException e) {
			return false;
		}
	}
}
