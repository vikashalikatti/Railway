package com.project.railway.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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

	private Long trainNumber;

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	private List<Seat> seats;

	private String passengerName;
	private Long contactNumber;
	private LocalDateTime bookingTime;

	@ManyToOne
	private Train train;

	@ManyToOne
	private Customer customer;
}
