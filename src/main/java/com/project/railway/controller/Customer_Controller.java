package com.project.railway.controller;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.project.railway.dto.Customer;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.service.Customer_Service;

import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;
import jakarta.mail.internet.ParseException;

@RestController
@RequestMapping("customer")
@CrossOrigin
public class Customer_Controller {

	@Autowired
	Customer_Service customer_Service;

	@PostMapping("/signup")
	public ResponseEntity<ResponseStructure<Customer>>signup(@ModelAttribute Customer customer,@RequestPart MultipartFile pic ) throws Exception {
		return customer_Service.signup(customer,pic);
	}
	
	@PostMapping("/login")
	public ResponseEntity<ResponseStructure<Customer>>login(@RequestParam String email,@RequestParam String password) 
			throws FileNotFoundException, MalformedTemplateNameException, ParseException, IOException, TemplateException{
		return customer_Service.login(email,password);
	}
	@PostMapping("/verify_otp")
	public ResponseEntity<ResponseStructure<Customer>> verifyotp(@RequestParam String email, @RequestParam int otp) {
		return customer_Service.verifyotp(email, otp);
	}
	
	@PostMapping("/resendOtp")
	public ResponseEntity<ResponseStructure<Customer>>resendOtp(@RequestParam String email) throws Throwable{
		return customer_Service.resendOtp(email);
	}
}
