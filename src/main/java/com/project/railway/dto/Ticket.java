package com.project.railway.dto;

import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Data;

@Data
@Entity
@Component
public class Ticket {
	@Id
	private Long ticketId;
	private Long bookingId;
	@OneToMany
	private List<Seat> seats;
	private double totalAmount;
	@OneToOne
	private Booking booking; // One-to-One relationship with BookingDTO

}
