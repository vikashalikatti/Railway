package com.project.railway.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The station directory: one row per real station, e.g. SBC / KSR Bengaluru City Jn / Bengaluru.
 * Train stops (Station) point to it by code, so passengers can search by code, name or city.
 */
@Entity
@Table(name = "station_master", indexes = { @Index(name = "idx_station_master_city", columnList = "city"),
		@Index(name = "idx_station_master_name", columnList = "name") })
@Getter
@Setter
public class StationMaster {

	/** Station code, 2 to 5 capital letters (SBC, UBL, MAS…) */
	@Id
	@Column(length = 6)
	private String code;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(nullable = false, length = 60)
	private String city;

	@Column(length = 40)
	private String state;

	/** Map position, used to estimate the km between two stops. Optional. */
	private Double latitude;
	private Double longitude;

	public StationMaster() {
	}

	public StationMaster(String code, String name, String city, String state) {
		this.code = code;
		this.name = name;
		this.city = city;
		this.state = state;
	}

	public StationMaster(String code, String name, String city, String state, Double latitude, Double longitude) {
		this(code, name, city, state);
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public boolean hasPosition() {
		return latitude != null && longitude != null;
	}
}
