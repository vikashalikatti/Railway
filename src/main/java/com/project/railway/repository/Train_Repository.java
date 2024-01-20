package com.project.railway.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.railway.dto.Train;

public interface Train_Repository extends JpaRepository<Train, Integer> {
	Train findByTrainName(String train_no);
}
