package com.project.railway.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.project.railway.dto.SeatOccupancy;

public interface SeatOccupancy_Repository extends JpaRepository<SeatOccupancy, Long> {

	@Transactional
	@Modifying
	@Query("delete from SeatOccupancy o where o.passenger.id in :passengerIds")
	int deleteByPassengerIds(@Param("passengerIds") Collection<Long> passengerIds);
}
