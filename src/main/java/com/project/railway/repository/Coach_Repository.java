package com.project.railway.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.railway.dto.Coach;

public interface Coach_Repository extends JpaRepository<Coach, Integer> {

}