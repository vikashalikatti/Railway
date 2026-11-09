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

	/** Waiting passengers for a train, date, class and quota, oldest WL number first, locked for promotion */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Passenger p join p.booking b where b.trainNumber = :train and b.journeyDate = :date"
			+ " and b.seatType = :type and coalesce(b.quota, 'GN') = :quota and p.status = 'WL' order by p.wlNumber asc")
	List<Passenger> findWaitlistForUpdate(@Param("train") int train, @Param("date") LocalDate date,
			@Param("type") String type, @Param("quota") String quota);

	@Query("select count(p) from Passenger p join p.booking b where b.trainNumber = :train and b.journeyDate = :date"
			+ " and b.seatType = :type and coalesce(b.quota, 'GN') = :quota and p.status = 'WL'")
	long countWaitlisted(@Param("train") int train, @Param("date") LocalDate date, @Param("type") String type,
			@Param("quota") String quota);

	/** How many waiting passengers of the same quota are ahead of WL number :wl */
	@Query("select count(p) from Passenger p join p.booking b where b.trainNumber = :train and b.journeyDate = :date"
			+ " and b.seatType = :type and coalesce(b.quota, 'GN') = :quota and p.status = 'WL' and p.wlNumber < :wl")
	long countWaitlistedBefore(@Param("train") int train, @Param("date") LocalDate date, @Param("type") String type,
			@Param("quota") String quota, @Param("wl") int wl);

	/** Everyone on a train run who has a ticket or had one at charting, for the admin's chart */
	@Query("select p from Passenger p join fetch p.booking b where b.trainNumber = :train and b.journeyDate = :date"
			+ " and (p.status <> 'CAN' or p.cancelReason = 'CHART') order by b.seatType, p.coachNumber, p.seatNumber, p.id")
	List<Passenger> findForChart(@Param("train") int train, @Param("date") LocalDate date);

	/** Dates (in a range) on which someone is waiting in a class of a train */
	@Query("select distinct b.journeyDate from Passenger p join p.booking b where b.trainNumber = :train"
			+ " and b.seatType = :type and p.status = 'WL' and b.journeyDate between :from and :to order by b.journeyDate")
	List<LocalDate> findWaitlistDates(@Param("train") int train, @Param("type") String type, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

	/** Confirmed passengers per date in these coaches from a date on: rows [journeyDate, count]. Cancelled tickets don't count. */
	@Query("select b.journeyDate, count(p) from Passenger p join p.booking b where p.seat.coach.id in :coachIds"
			+ " and p.status = 'CNF' and b.journeyDate >= :from group by b.journeyDate order by b.journeyDate")
	List<Object[]> countConfirmedInCoachesFrom(@Param("coachIds") java.util.Collection<Integer> coachIds,
			@Param("from") LocalDate from);

	/** Passengers (any status, any date) still pointing at these seats */
	@Query("select count(p) from Passenger p where p.seat.id in :seatIds")
	long countBySeatIds(@Param("seatIds") java.util.Collection<Long> seatIds);

	/** Classes of a train run that still have someone waiting: rows [seatType, quota] */
	@Query("select distinct b.seatType, coalesce(b.quota, 'GN') from Passenger p join p.booking b"
			+ " where b.trainNumber = :train and b.journeyDate = :date and p.status = 'WL'")
	List<Object[]> findWaitingClasses(@Param("train") int train, @Param("date") LocalDate date);

	/** Everyone still waiting on a train run, locked (cancelled when the first chart is prepared) */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Passenger p join p.booking b where b.trainNumber = :train and b.journeyDate = :date"
			+ " and p.status = 'WL' order by p.id")
	List<Passenger> findAllWaitingForUpdate(@Param("train") int train, @Param("date") LocalDate date);

	/**
	 * People booked in a class on an overlapping stretch, with what matters for seating near them:
	 * rows [coach_number, seat_number, gender, age, needs_assistance, helper, companion_id]
	 */
	@Query(value = "SELECT c.coach_number, cs.seat_number, p.gender, p.age, p.needs_assistance, p.helper, p.companion_id"
			+ " FROM seat_occupancy o JOIN coach_seat cs ON cs.id = o.seat_id JOIN coach c ON c.id = cs.coach_id"
			+ " JOIN passenger p ON p.id = o.passenger_id WHERE o.train_number = :train AND o.journey_date = :date"
			+ " AND c.coach_type = :type AND o.from_stop < :toStop AND o.to_stop > :fromStop", nativeQuery = true)
	List<Object[]> findNeighbours(@Param("train") int train, @Param("date") LocalDate date, @Param("type") String type,
			@Param("fromStop") int fromStop, @Param("toStop") int toStop);

	/** Confirmed seniors who asked for help and volunteer helpers on a train run, not yet matched; locked */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Passenger p join fetch p.booking b where b.trainNumber = :train and b.journeyDate = :date"
			+ " and p.status = 'CNF' and p.companionId is null and (p.needsAssistance = true or p.helper = true)"
			+ " order by p.id")
	List<Passenger> findUnmatchedCompanionsForUpdate(@Param("train") int train, @Param("date") LocalDate date);
}
