package com.project.railway.dto;

import java.time.LocalTime;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity
@jakarta.persistence.Table(name = "station", indexes = @jakarta.persistence.Index(name = "idx_station_code", columnList = "station_code"))
@Data
@Component
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "stationId")
public class Station {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long stationId;
	private String stationName;

	/** Code of the station in the directory (StationMaster), e.g. SBC. Null for stops typed before codes existed. */
	@jakarta.persistence.Column(name = "station_code", length = 6)
	private String stationCode;
	private LocalTime departureTime;
	/** Day the train reaches this stop (1 = the day it leaves its first station); empty = worked out from the times */
	@jakarta.persistence.Column(name = "day_number")
	private Integer day;
	private LocalTime arrivalTime;
	private double km;
	private double price;

	@ManyToOne(cascade = CascadeType.PERSIST)
	private Route route;

	@ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	private Train trains;

}
