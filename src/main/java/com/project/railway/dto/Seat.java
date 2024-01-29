package com.project.railway.dto;

import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Data;

@Entity
@Data
@Component
public class Seat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long seatId;
	private int sleeper_class;
	private int ac3_tier;
	private int ac2_tier;
	private int ac1_tier;
	private int second_class;
	private boolean second_class_isAvailable;
	private int total_seat;

	@OneToOne(mappedBy = "seat")
	private Train train;

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	private List<Booking> bookings;

}