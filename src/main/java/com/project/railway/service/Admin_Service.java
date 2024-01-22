package com.project.railway.service;

import java.io.IOException;

import org.springframework.http.ResponseEntity;

import com.project.railway.dto.Admin;
import com.project.railway.dto.Train;
import com.project.railway.helper.ResponseStructure;

import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateNotFoundException;

public interface Admin_Service {

	ResponseEntity<ResponseStructure<Admin>> create(Admin admin);

	ResponseEntity<ResponseStructure<Admin>> login(String email, String password)
			throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException;

	ResponseEntity<ResponseStructure<Train>> trainadd(Train train, String token);

}
