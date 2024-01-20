package com.project.railway.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.railway.dto.Customer;

public interface Customer_Repository extends JpaRepository<Customer, Integer> {

	Customer findByEmail(String email);

	Customer findByMobile(long mobile);

}
