package com.project.railway.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.railway.dto.Payment;

import jakarta.persistence.LockModeType;

public interface Payment_Repository extends JpaRepository<Payment, String> {

	/** SELECT … FOR UPDATE, so one payment can't pay for two bookings */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.id = :id")
	Payment lock(@Param("id") String id);

	/** Paid but never used for a booking (refunded automatically after a while) */
	List<Payment> findByStatusAndPaidAtBefore(String status, LocalDateTime before);

	List<Payment> findByCustomerIdOrderByCreatedAtDesc(int customerId);
}
