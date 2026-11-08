package com.project.railway.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.railway.dto.Booking;
import com.project.railway.dto.Customer;

public interface Book_Repository extends JpaRepository<Booking, Long> {

	List<Booking> findByCustomerOrderByBookedAtDesc(Customer customer);

	boolean existsByTrainNumber(int trainNumber);

	/** Tickets for today or later that aren't cancelled */
	List<Booking> findByTrainNumberAndJourneyDateGreaterThanEqualAndStatusNot(int trainNumber, LocalDate from,
			String status);
}
