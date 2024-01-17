package com.project.railway.dto;

import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity
@Data
@Component
public class Booking {
	@Id
	private Long bookingId;
	private Long trainId;
	private List<Long> seatIds;
	private String passengerName;
	private String contactNumber;
	@ManyToOne
	private Train train; // Many-to-One relationship with TrainDTO
	@ManyToMany
	private List<Seat> seats; // Many-to-Many relationship with SeatDTO
}
