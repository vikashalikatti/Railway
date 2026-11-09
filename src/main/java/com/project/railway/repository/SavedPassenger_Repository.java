package com.project.railway.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.railway.dto.Customer;
import com.project.railway.dto.SavedPassenger;

public interface SavedPassenger_Repository extends JpaRepository<SavedPassenger, Long> {

	List<SavedPassenger> findByCustomerOrderByNameAsc(Customer customer);

	long countByCustomer(Customer customer);
}
