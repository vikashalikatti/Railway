package com.project.railway.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.railway.dto.TrainRun;

public interface TrainRun_Repository extends JpaRepository<TrainRun, Long> {

	TrainRun findByTrainNumberAndJourneyDate(int trainNumber, LocalDate journeyDate);
}
