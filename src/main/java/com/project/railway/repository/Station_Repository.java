package com.project.railway.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.railway.dto.Station;

public interface Station_Repository extends JpaRepository<Station, Long> {

	List<Station> findByStationName(String start);

}
