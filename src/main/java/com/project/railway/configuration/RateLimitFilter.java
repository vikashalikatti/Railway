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
 * The client is the connecting IP address. X-Forwarded-For is only trusted from a proxy we know,
 * because anyone else could put any address there: from this machine (the Vite dev proxy adds it)
 * and, when TRUST_PROXY=true, from private network addresses (nginx in Docker, a Kubernetes
 * ingress). The last address in the header is used: the one our own proxy wrote.
 */
public class RateLimitFilter extends OncePerRequestFilter {

	private record Rule(String method, String prefix, RateLimiter limiter) {
	}

	private final Map<String, Rule> rules = new LinkedHashMap<>();
	private final boolean trustPrivateProxies;

	public RateLimitFilter() {
		this(false);
	}

	public RateLimitFilter(boolean trustPrivateProxies) {
		this.trustPrivateProxies = trustPrivateProxies;
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
				if (!r.limiter().allow(r.prefix() + "|" + client(req, trustPrivateProxies), System.currentTimeMillis())) {
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

	static String client(HttpServletRequest req, boolean trustPrivateProxies) {
		String remote = req.getRemoteAddr();
		String forwarded = req.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank() && fromProxy(remote, trustPrivateProxies)) {
			String[] hops = forwarded.split(",");
			String last = hops[hops.length - 1].trim();
			if (!last.isEmpty() && last.length() <= 45) {
				return last;
			}
		}
		return remote;
	}

	private static boolean fromProxy(String ip, boolean trustPrivate) {
		try {
			if (ip == null) {
				return false;
			}
			InetAddress a = InetAddress.getByName(ip);
			return a.isLoopbackAddress() || (trustPrivate && a.isSiteLocalAddress());
		} catch (IOException e) {
			return false;
		}
	}
}
