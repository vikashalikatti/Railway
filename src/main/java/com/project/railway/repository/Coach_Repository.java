package com.project.railway.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.project.railway.dto.Coach;

public interface Coach_Repository extends JpaRepository<Coach, Integer> {

	/** Coaches created before backend v1.2.0 (the old "total seats" step): they have no seat rows */
	@Query("select c from Coach c where not exists (select s.id from CoachSeat s where s.coach = c) order by c.id")
	List<Coach> findCoachesWithoutSeats();
}
