package com.project.railway.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.railway.dto.Passenger;

import jakarta.persistence.LockModeType;

public interface Passenger_Repository extends JpaRepository<Passenger, Long> {

	/** Waiting passengers for a train, date and class, oldest WL number first, locked for promotion */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Passenger p join p.booking b where b.trainNumber = :train and b.journeyDate = :date"
			+ " and b.seatType = :type and p.status = 'WL' order by p.wlNumber asc")
	List<Passenger> findWaitlistForUpdate(@Param("train") int train, @Param("date") LocalDate date,
			@Param("type") String type);

	@Query("select count(p) from Passenger p join p.booking b where b.trainNumber = :train and b.journeyDate = :date"
			+ " and b.seatType = :type and p.status = 'WL'")
	long countWaitlisted(@Param("train") int train, @Param("date") LocalDate date, @Param("type") String type);

	/** How many waiting passengers are ahead of WL number :wl */
	@Query("select count(p) from Passenger p join p.booking b where b.trainNumber = :train and b.journeyDate = :date"
			+ " and b.seatType = :type and p.status = 'WL' and p.wlNumber < :wl")
	long countWaitlistedBefore(@Param("train") int train, @Param("date") LocalDate date, @Param("type") String type,
			@Param("wl") int wl);
}
