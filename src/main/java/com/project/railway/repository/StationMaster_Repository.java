package com.project.railway.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.railway.dto.StationMaster;

public interface StationMaster_Repository extends JpaRepository<StationMaster, String> {

	/** Live search: exact code first, then code prefix, then city prefix, then anything containing the text */
	@Query("select s from StationMaster s where upper(s.code) like concat(upper(:q), '%')"
			+ " or upper(s.name) like concat('%', upper(:q), '%') or upper(s.city) like concat(upper(:q), '%')"
			+ " order by case when upper(s.code) = upper(:q) then 0"
			+ " when upper(s.code) like concat(upper(:q), '%') then 1"
			+ " when upper(s.city) like concat(upper(:q), '%') then 2"
			+ " when upper(s.name) like concat(upper(:q), '%') then 3 else 4 end, s.city, s.name")
	List<StationMaster> search(@Param("q") String q, Pageable page);

	Optional<StationMaster> findByCodeIgnoreCase(String code);

	List<StationMaster> findByCityIgnoreCaseOrderByName(String city);

	Optional<StationMaster> findFirstByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCase(String name);
}
