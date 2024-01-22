package com.project.railway.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.railway.dto.Admin;
import com.project.railway.dto.Train;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.service.Admin_Service;

import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateNotFoundException;

@RestController
@RequestMapping("admin")
@CrossOrigin
public class Admin_Controller {
	@Autowired
	Admin_Service admin_Service;

	@PostMapping("/create")
	public ResponseEntity<ResponseStructure<Admin>> create(@RequestBody Admin admin) {
		return admin_Service.create(admin);
	}

	@PostMapping("/login")
	public ResponseEntity<ResponseStructure<Admin>> login(@RequestParam String name, @RequestParam String password)
			throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException {
		return admin_Service.login(name, password);
	}

	@PostMapping("/addtrain")
	public ResponseEntity<ResponseStructure<Train>> trainadd(@ModelAttribute Train train,
			@RequestHeader("Bearer") String token) {
		return admin_Service.trainadd(train, token);
	}

}
