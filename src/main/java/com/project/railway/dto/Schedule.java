package com.project.railway.dto;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity
@Component
@Data
public class Schedule {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long scheduleId;
	private int trainNumber;
	private LocalDateTime departureTime;
	private LocalDateTime arrivalTime;
	@ManyToOne
	private Station station; // Many-to-One relationship with StationDTO
	@ManyToOne
	private Train train; // Many-to-One relationship with TrainDTO
}
