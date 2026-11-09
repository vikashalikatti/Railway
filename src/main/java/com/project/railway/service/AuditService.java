package com.project.railway.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.railway.dto.AuditLog;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.repository.AuditLog_Repository;

/**
 * Audit log of admin actions: who added or changed a train, fares, coaches, quotas, cancelled or
 * rescheduled a run, imported trains, blocked a customer, and when. Entries are written in the same
 * transaction as the change, so a change that fails leaves no entry.
 */
@Service
public class AuditService {

	private static final Logger log = LoggerFactory.getLogger(AuditService.class);
	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	@Autowired
	private AuditLog_Repository repository;
	@Autowired
	private JwtUtil jwtUtil;

	/** Records an action by the admin holding this token (never throws) */
	public void record(String token, String action, String target, String details) {
		String[] who = jwtUtil.getSubject(token);
		recordAs(who == null ? "unknown" : who[0], action, target, details);
	}

	public void recordAs(String actor, String action, String target, String details) {
		try {
			AuditLog a = new AuditLog();
			a.setAt(LocalDateTime.now(IST));
			a.setActor(cut(actor, 60));
			a.setAction(cut(action, 40));
			a.setTarget(cut(target, 80));
			a.setDetails(cut(details, 500));
			repository.save(a);
		} catch (RuntimeException e) {
			log.warn("Audit entry {} {} not saved: {}", action, target, e.toString());
		}
	}

	@Transactional(readOnly = true)
	public ResponseEntity<ResponseStructure<List<AuditLog>>> list(String token, String q, int limit) {
		ResponseStructure<List<AuditLog>> body = new ResponseStructure<>();
		String[] who = jwtUtil.getSubject(token);
		if (who == null || !"admin".equals(who[1])) {
			body.setStatus(401);
			body.setMessage("Invalid or Expired Token, Please Login Again");
			return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
		}
		String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
		List<AuditLog> rows = repository.search(query.length() > 60 ? query.substring(0, 60) : query,
				PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
		body.setStatus(200);
		body.setMessage(rows.size() + " entries");
		body.setData2(rows);
		return ResponseEntity.ok(body);
	}

	private static String cut(String s, int max) {
		if (s == null) {
			return null;
		}
		String c = s.replaceAll("[\\r\\n]+", " ").trim();
		return c.length() > max ? c.substring(0, max) : c;
	}
}
