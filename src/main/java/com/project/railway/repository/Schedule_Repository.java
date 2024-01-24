package com.project.railway.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.railway.dto.Schedule;

public interface Schedule_Repository extends JpaRepository<Schedule, Long> {

	Schedule findByTrainTrainNumber(int trainNo);

}
