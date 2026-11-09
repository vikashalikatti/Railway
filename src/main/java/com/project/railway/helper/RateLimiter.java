package com.project.railway.helper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Requests allowed per client per minute, per rule (token bucket). Plain Java so it can be
 * unit-tested; the clock is passed in.
 */
public final class RateLimiter {

	private final int perMinute;
	private final Map<String, double[]> buckets = new ConcurrentHashMap<>(); // key -> [tokens, last ms]
	private long lastCleanup;

	public RateLimiter(int perMinute) {
		this.perMinute = perMinute;
	}

	/** True if this request is allowed; false if the client should wait */
	public boolean allow(String key, long nowMs) {
		cleanup(nowMs);
		double[] b = buckets.computeIfAbsent(key, k -> new double[] { perMinute, nowMs });
		synchronized (b) {
			double refill = (nowMs - b[1]) * perMinute / 60000.0;
			b[0] = Math.min(perMinute, b[0] + refill);
			b[1] = nowMs;
			if (b[0] >= 1) {
				b[0] -= 1;
				return true;
			}
			return false;
		}
	}

	/** Seconds until the next request is allowed */
	public int retryAfterSeconds() {
		return Math.max(1, (int) Math.ceil(60.0 / perMinute));
	}

	private void cleanup(long nowMs) {
		if (nowMs - lastCleanup < 60000) {
			return;
		}
		lastCleanup = nowMs;
		buckets.entrySet().removeIf(e -> nowMs - e.getValue()[1] > 120000);
	}
}
