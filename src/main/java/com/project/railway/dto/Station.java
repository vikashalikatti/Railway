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
@Data
@Component
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "stationId")
public class Station {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long stationId;
	private String stationName;
	private LocalTime departureTime;
	private LocalTime arrivalTime;
	private double km;
	private double price;

	@ManyToOne(cascade = CascadeType.PERSIST)
	private Route route;

	@ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	private Train trains;

}
