package com.project.railway.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.railway.dto.Customer;

public interface Customer_Repository extends JpaRepository<Customer, Integer> {

	Customer findByEmail(String email);

	Customer findByMobile(long mobile);

	/** Admin search: name or email containing q, or mobile number starting with q; newest first */
	@org.springframework.data.jpa.repository.Query("select c from Customer c where :q = ''"
			+ " or lower(c.name) like concat('%', :q, '%') or lower(c.email) like concat('%', :q, '%')"
			+ " or cast(c.mobile as string) like concat(:q, '%') order by c.id desc")
	java.util.List<Customer> search(@org.springframework.data.repository.query.Param("q") String lowerCaseQuery,
			org.springframework.data.domain.Pageable page);

}
