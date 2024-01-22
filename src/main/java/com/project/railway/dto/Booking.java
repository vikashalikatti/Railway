package com.project.railway.dto;

import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.Data;

@Entity
@Data
@Component
public class Booking {
	@Id
	@GeneratedValue(generator = "pnr")
	@SequenceGenerator(initialValue = 456789211, allocationSize = 1, sequenceName = "pnr", name = "pnr")
	private Long bookingId;
	private Long trinNumber;
	private List<Long> seatIds;
	private String passengerName;
	private Long contactNumber;
	@ManyToOne
	private Train train; // Many-to-One relationship with TrainDTO
	@ManyToMany
	private List<Seat> seats; // Many-to-Many relationship with SeatDTO
}
