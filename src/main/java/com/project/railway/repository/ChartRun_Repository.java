package com.project.railway.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.project.railway.dto.ChartRun;

import jakarta.persistence.LockModeType;

public interface ChartRun_Repository extends JpaRepository<ChartRun, Long> {

	/** Creates the row if it doesn't exist yet. INSERT IGNORE never fails on a duplicate. */
	@Transactional
	@Modifying
	@Query(value = "INSERT IGNORE INTO chart_run (train_number, journey_date, stage) VALUES (:train, :date, 0)",
			nativeQuery = true)
	int ensureExists(@Param("train") int train, @Param("date") LocalDate date);

	/** SELECT ... FOR UPDATE on the row */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from ChartRun c where c.trainNumber = :train and c.journeyDate = :date")
	ChartRun lock(@Param("train") int train, @Param("date") LocalDate date);

	ChartRun findByTrainNumberAndJourneyDate(int trainNumber, LocalDate journeyDate);

	/** Chart rows for a few days, read once per scheduler pass */
	java.util.List<ChartRun> findByJourneyDateBetween(LocalDate from, LocalDate to);
}
