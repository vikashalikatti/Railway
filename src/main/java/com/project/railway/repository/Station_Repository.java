package com.project.railway.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.railway.dto.Station;

public interface Station_Repository extends JpaRepository<Station, Long> {

	List<Station> findByStationName(String start);

	/** Stops typed before station codes existed */
	List<Station> findByStationCodeIsNull();

	/** Stop names without a code that contain the text (so old trains still show up in live search) */
	@Query("select distinct s.stationName from Station s where s.stationCode is null"
			+ " and upper(s.stationName) like concat('%', upper(:q), '%') order by s.stationName")
	List<String> searchUncodedNames(@Param("q") String q, Pageable page);

}
