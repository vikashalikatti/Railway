package com.project.railway.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.railway.dto.QuotaShare;

public interface QuotaShare_Repository extends JpaRepository<QuotaShare, Long> {

	List<QuotaShare> findByTrainNumber(int trainNumber);

	QuotaShare findByTrainNumberAndSeatType(int trainNumber, String seatType);
}
