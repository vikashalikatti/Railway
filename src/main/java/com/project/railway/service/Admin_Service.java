package com.project.railway.service;

import org.springframework.http.ResponseEntity;

import com.project.railway.dto.Admin;
import com.project.railway.helper.ResponseStructure;

public interface Admin_Service {

	ResponseEntity<ResponseStructure<Admin>> create(Admin admin);

}
