package com.project.railway.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * The admin's quota split for one class of one train, in percent of the class's seats.
 * General gets whatever is left. Without a row the defaults in QuotaPlanner.Shares apply.
 */
@Entity
@Table(name = "quota_share", uniqueConstraints = @UniqueConstraint(name = "uk_quota_share", columnNames = {
		"train_number", "seat_type" }))
@Getter
@Setter
public class QuotaShare {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "train_number", nullable = false)
	private int trainNumber;
	@Column(name = "seat_type", nullable = false, length = 20)
	private String seatType;
	@Column(name = "tatkal_pct", nullable = false)
	private int tatkalPct;
	@Column(name = "premium_pct", nullable = false)
	private int premiumPct;
	@Column(name = "ladies_pct", nullable = false)
	private int ladiesPct;
	@Column(name = "senior_pct", nullable = false)
	private int seniorPct;

	/** RAC berths per coach; empty = the default for the class */
	@Column(name = "rac_berths")
	private Integer racBerths;

	/** Divyangjan share in percent; empty = the default for the class */
	@Column(name = "divyang_pct")
	private Integer divyangPct;
}
