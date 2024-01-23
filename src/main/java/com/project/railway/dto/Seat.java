package com.project.railway.dto;

import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Data;

@Entity
@Data
@Component
public class Seat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long seatId;
	private String seatNumber;
	private String coachType;
	private boolean isAvailable;

	@ManyToOne
	private Train train; // Many-to-One relationship with Trai

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Booking> bookings;
}
