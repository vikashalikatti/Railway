package com.project.railway.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.railway.dto.AuditLog;

public interface AuditLog_Repository extends JpaRepository<AuditLog, Long> {

	/** Newest first; q matches the admin, action, target or details */
	@Query("select a from AuditLog a where :q = '' or lower(a.actor) like concat('%', :q, '%')"
			+ " or lower(a.action) like concat('%', :q, '%') or lower(a.target) like concat('%', :q, '%')"
			+ " or lower(a.details) like concat('%', :q, '%') order by a.id desc")
	List<AuditLog> search(@Param("q") String lowerCaseQuery, Pageable page);
}
