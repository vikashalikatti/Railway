package com.project.railway.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.project.railway.dto.WaitlistCounter;

import jakarta.persistence.LockModeType;

public interface WaitlistCounter_Repository extends JpaRepository<WaitlistCounter, Long> {

	/** Creates the counter row if it doesn't exist yet. INSERT IGNORE never fails on a duplicate. */
	@Transactional
	@Modifying
	@Query(value = "INSERT IGNORE INTO waitlist_counter (train_number, journey_date, seat_type, last_number)"
			+ " VALUES (:train, :date, :type, 0)", nativeQuery = true)
	int ensureExists(@Param("train") int train, @Param("date") LocalDate date, @Param("type") String type);

	/** SELECT ... FOR UPDATE on the counter row */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from WaitlistCounter w where w.trainNumber = :train and w.journeyDate = :date and w.seatType = :type")
	WaitlistCounter lock(@Param("train") int train, @Param("date") LocalDate date, @Param("type") String type);
}
