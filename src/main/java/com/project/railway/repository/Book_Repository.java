package com.project.railway.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.railway.dto.Booking;

public interface Book_Repository extends JpaRepository<Booking, Integer> {

}
