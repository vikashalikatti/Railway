package com.project.railway.dto;

import java.util.Map;

/**
 * Quota split of one class of a train, for the admin page (response only, not stored).
 * Percentages of the class's seats; General gets the rest. seatsByQuota: how many seats each
 * quota has right now (GN, TQ, PT, LD, SS).
 */
public record QuotaView(String seatType, int tatkalPct, int premiumPct, int ladiesPct, int seniorPct, int racBerths,
		int divyangPct, boolean saved, long seats, Map<String, Long> seatsByQuota) {
}
