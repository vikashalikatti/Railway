package com.project.railway.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.project.railway.dto.CoachSeat;

/**
 * Seat inventory queries (MySQL 8).
 *
 * "Free for a segment" means: no SeatOccupancy for the same seat and date whose stops
 * overlap ours (o.from_stop < :toStop AND o.to_stop > :fromStop).
 *
 * Locking queries use FOR UPDATE ... SKIP LOCKED: a seat another booking has locked
 * is skipped instead of waited on, so people booking at the same moment get different
 * seats without queueing. "OF cs" locks only coach_seat rows, never the coach row.
 */
public interface CoachSeat_Repository extends JpaRepository<CoachSeat, Long> {

	String FREE_FOR_SEGMENT = " NOT EXISTS (SELECT 1 FROM seat_occupancy o WHERE o.seat_id = cs.id"
			+ " AND o.journey_date = :date AND o.from_stop < :toStop AND o.to_stop > :fromStop) ";

	/**
	 * Seats sold in this quota. :anyQuota = true after the first chart, when unsold seats of every
	 * quota are released (seats with no quota set count as General).
	 */
	/** Only coaches running on the journey date (extra coaches for rush days, removed coaches) */
	String COACH_RUNS = " AND (c.valid_from IS NULL OR c.valid_from <= :date) AND (c.valid_to IS NULL OR c.valid_to >= :date) ";

	String IN_QUOTA = " AND (:anyQuota = TRUE OR COALESCE(cs.quota, 'GN') = :quota) ";

	/** Free seats for a segment, per coach of one class. Rows: [coach_id, free_seats]. No locks. */
	@Query(value = "SELECT cs.coach_id, COUNT(*) AS free_seats FROM coach_seat cs"
			+ " JOIN coach c ON c.id = cs.coach_id"
			+ " WHERE c.train_number = :train AND c.coach_type = :type AND" + FREE_FOR_SEGMENT + IN_QUOTA + COACH_RUNS
			+ " GROUP BY cs.coach_id ORDER BY cs.coach_id", nativeQuery = true)
	List<Object[]> countFreeByCoach(@Param("train") int train, @Param("type") String type,
			@Param("date") LocalDate date, @Param("fromStop") int fromStop, @Param("toStop") int toStop,
			@Param("quota") String quota, @Param("anyQuota") boolean anyQuota);

	/** Free seats for a segment in one class. No locks (used to show availability). */
	@Query(value = "SELECT COUNT(*) FROM coach_seat cs JOIN coach c ON c.id = cs.coach_id"
			+ " WHERE c.train_number = :train AND c.coach_type = :type AND" + FREE_FOR_SEGMENT + IN_QUOTA + COACH_RUNS,
			nativeQuery = true)
	long countFree(@Param("train") int train, @Param("type") String type, @Param("date") LocalDate date,
			@Param("fromStop") int fromStop, @Param("toStop") int toStop, @Param("quota") String quota,
			@Param("anyQuota") boolean anyQuota);

	/** Seats of a class that belong to a quota, booked or not */
	@Query(value = "SELECT COUNT(*) FROM coach_seat cs JOIN coach c ON c.id = cs.coach_id"
			+ " WHERE c.train_number = :train AND c.coach_type = :type AND COALESCE(cs.quota, 'GN') = :quota",
			nativeQuery = true)
	long countSeatsInQuota(@Param("train") int train, @Param("type") String type, @Param("quota") String quota);

	/** Every seat of the coaches running on a date, coach by coach (for the reservation chart) */
	@Query("select s from CoachSeat s join fetch s.coach c where c.train.trainNumber = :train"
			+ " and (c.validFrom is null or c.validFrom <= :date) and (c.validTo is null or c.validTo >= :date)"
			+ " order by c.id, s.seatNumber")
	List<CoachSeat> findSeatsOfTrainOn(@Param("train") int train, @Param("date") LocalDate date);

	/** Seats of a class in the coaches running on a date. 0: the class doesn't run that day. */
	@Query(value = "SELECT COUNT(*) FROM coach_seat cs JOIN coach c ON c.id = cs.coach_id"
			+ " WHERE c.train_number = :train AND c.coach_type = :type" + COACH_RUNS, nativeQuery = true)
	long countSeatsInClassOn(@Param("train") int train, @Param("type") String type, @Param("date") LocalDate date);

	/** Seats of a class in a quota, in the coaches running on a date */
	@Query(value = "SELECT COUNT(*) FROM coach_seat cs JOIN coach c ON c.id = cs.coach_id"
			+ " WHERE c.train_number = :train AND c.coach_type = :type AND COALESCE(cs.quota, 'GN') = :quota" + COACH_RUNS,
			nativeQuery = true)
	long countSeatsInQuotaOn(@Param("train") int train, @Param("type") String type, @Param("quota") String quota,
			@Param("date") LocalDate date);

	/** Ids of every seat in these coaches */
	@Query("select s.id from CoachSeat s where s.coach.id in :coachIds")
	List<Long> findIdsByCoachIds(@Param("coachIds") Collection<Integer> coachIds);

	/** Every seat of a class in coach-then-seat order, for splitting into quotas */
	@Query("select s from CoachSeat s where s.coach.train.trainNumber = :train and s.coach.coachType = :type"
			+ " order by s.coach.id, s.seatNumber")
	List<CoachSeat> findSeatsInClass(@Param("train") int train, @Param("type") String type);

	/** All seats in a class, booked or not. 0 means the class's coaches have no seats set up. */
	@Query(value = "SELECT COUNT(*) FROM coach_seat cs JOIN coach c ON c.id = cs.coach_id"
			+ " WHERE c.train_number = :train AND c.coach_type = :type", nativeQuery = true)
	long countSeatsInClass(@Param("train") int train, @Param("type") String type);

	/** Locks up to :lim free seats in one coach, skipping seats other bookings have locked. */
	@Query(value = "SELECT cs.* FROM coach_seat cs"
			+ " WHERE cs.coach_id = :coachId AND cs.id NOT IN (:exclude) AND" + FREE_FOR_SEGMENT + IN_QUOTA
			+ " ORDER BY cs.seat_number LIMIT :lim FOR UPDATE SKIP LOCKED", nativeQuery = true)
	List<CoachSeat> lockFreeInCoach(@Param("coachId") int coachId, @Param("exclude") Collection<Long> exclude,
			@Param("date") LocalDate date, @Param("fromStop") int fromStop, @Param("toStop") int toStop,
			@Param("quota") String quota, @Param("anyQuota") boolean anyQuota, @Param("lim") int lim);

	/** Locks up to :lim free seats in any coach of the class, skipping locked ones. */
	@Query(value = "SELECT cs.* FROM coach_seat cs JOIN coach c ON c.id = cs.coach_id"
			+ " WHERE c.train_number = :train AND c.coach_type = :type AND cs.id NOT IN (:exclude) AND"
			+ FREE_FOR_SEGMENT + IN_QUOTA + COACH_RUNS + " ORDER BY c.id, cs.seat_number LIMIT :lim FOR UPDATE OF cs SKIP LOCKED",
			nativeQuery = true)
	List<CoachSeat> lockFreeInClass(@Param("train") int train, @Param("type") String type,
			@Param("exclude") Collection<Long> exclude, @Param("date") LocalDate date, @Param("fromStop") int fromStop,
			@Param("toStop") int toStop, @Param("quota") String quota, @Param("anyQuota") boolean anyQuota,
			@Param("lim") int lim);

	/**
	 * Same as lockFreeInClass but WAITS for seats other bookings hold, instead of skipping
	 * them. Only used when the class is nearly full, so nobody is waitlisted while free
	 * seats are just briefly locked by someone else's booking.
	 */
	@Query(value = "SELECT cs.* FROM coach_seat cs JOIN coach c ON c.id = cs.coach_id"
			+ " WHERE c.train_number = :train AND c.coach_type = :type AND cs.id NOT IN (:exclude) AND"
			+ FREE_FOR_SEGMENT + IN_QUOTA + COACH_RUNS + " ORDER BY cs.id LIMIT :lim FOR UPDATE OF cs", nativeQuery = true)
	List<CoachSeat> lockFreeInClassWaiting(@Param("train") int train, @Param("type") String type,
			@Param("exclude") Collection<Long> exclude, @Param("date") LocalDate date, @Param("fromStop") int fromStop,
			@Param("toStop") int toStop, @Param("quota") String quota, @Param("anyQuota") boolean anyQuota,
			@Param("lim") int lim);

	/**
	 * Of the given (already locked) seats, the ones that are NOT free for the segment.
	 * Run after locking: it reads the latest committed data, so it catches a booking
	 * that committed between our search and our lock.
	 */
	@Query(value = "SELECT DISTINCT o.seat_id FROM seat_occupancy o WHERE o.seat_id IN (:ids)"
			+ " AND o.journey_date = :date AND o.from_stop < :toStop AND o.to_stop > :fromStop", nativeQuery = true)
	List<Number> findTaken(@Param("ids") Collection<Long> ids, @Param("date") LocalDate date,
			@Param("fromStop") int fromStop, @Param("toStop") int toStop);

	/** Locks specific seats, waiting if needed (cancellation). Ordered by id to avoid deadlocks. */
	@Query(value = "SELECT cs.* FROM coach_seat cs WHERE cs.id IN (:ids) ORDER BY cs.id FOR UPDATE", nativeQuery = true)
	List<CoachSeat> lockByIds(@Param("ids") Collection<Long> ids);

	@Transactional
	@Modifying
	@Query("delete from CoachSeat s where s.coach.id in :coachIds")
	int deleteByCoachIds(@Param("coachIds") Collection<Integer> coachIds);
}
