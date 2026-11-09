package com.project.railway.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.railway.dto.Train;

public interface Train_Repository extends JpaRepository<Train, Integer> {
	Train findByTrainName(String train_no);

	Train findByTrainNumber(int train_No);

	@Query("select t.trainNumber from Train t")
	List<Integer> findAllNumbers();

	/** Admin search: number starting with q, or name / first / last station containing q */
	@Query("select t from Train t where :q = '' or cast(t.trainNumber as string) like concat(:q, '%')"
			+ " or lower(t.trainName) like concat('%', :q, '%') or lower(t.sourceStation) like concat('%', :q, '%')"
			+ " or lower(t.destinationStation) like concat('%', :q, '%') order by t.trainNumber")
	List<Train> search(@Param("q") String lowerCaseQuery, Pageable page);

	@Query("select count(t) from Train t where :q = '' or cast(t.trainNumber as string) like concat(:q, '%')"
			+ " or lower(t.trainName) like concat('%', :q, '%') or lower(t.sourceStation) like concat('%', :q, '%')"
			+ " or lower(t.destinationStation) like concat('%', :q, '%')")
	long countSearch(@Param("q") String lowerCaseQuery);

	/**
	 * Trains with a stop at one of these stations (codes in capitals, names in lower case), so searches
	 * only load the trains that can match instead of every train.
	 */
	@Query("select distinct t.trainNumber from Train t join t.stations s where upper(s.stationCode) in :codes"
			+ " or lower(trim(s.stationName)) in :names")
	List<Integer> findNumbersStoppingAt(@Param("codes") Collection<String> codes,
			@Param("names") Collection<String> names);

	/**
	 * For the chart scheduler, without loading every stop: rows [trainNumber, first stop's departure, running days]
	 */
	@Query("select t.trainNumber, s.departureTime, sc.runningWeeks from Train t join t.schedule sc join t.stations s"
			+ " where s.stationId = (select min(s2.stationId) from Train t2 join t2.stations s2"
			+ " where t2.trainNumber = t.trainNumber)")
	List<Object[]> findStartTimes();
}
