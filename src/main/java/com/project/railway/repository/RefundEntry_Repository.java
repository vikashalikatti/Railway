package com.project.railway.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.railway.dto.RefundEntry;

public interface RefundEntry_Repository extends JpaRepository<RefundEntry, Long> {

	List<RefundEntry> findByBookingIdOrderByRefundedAtAsc(Long bookingId);
}
