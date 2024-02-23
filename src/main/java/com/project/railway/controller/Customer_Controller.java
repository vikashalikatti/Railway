package com.project.railway.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.project.railway.dto.Booking;
import com.project.railway.dto.Customer;
import com.project.railway.dto.Seat;
import com.project.railway.dto.Train;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.service.Customer_Service;

import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateNotFoundException;
import jakarta.mail.internet.ParseException;

@RestController
@RequestMapping("customer")
@CrossOrigin
public class Customer_Controller {

	@Autowired
	Customer_Service customer_Service;

	@PostMapping("/signup")
	public ResponseEntity<ResponseStructure<Customer>> signup(@ModelAttribute Customer customer,
			@RequestPart MultipartFile pic) throws Exception {
		return customer_Service.signup(customer, pic);
	}

	@PostMapping("/login")
	public ResponseEntity<ResponseStructure<Customer>> login(@RequestParam String name, @RequestParam String password)
			throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException {
		return customer_Service.login(name, password);
	}

	@PostMapping("/verify_otp")
	public ResponseEntity<ResponseStructure<Customer>> verifyotp(@RequestParam String email, @RequestParam int otp) {
		return customer_Service.verifyotp(email, otp);
	}

	@PostMapping("/forgot_passowrd")
	public ResponseEntity<ResponseStructure<Customer>> forgot_password(@RequestParam String email) throws Exception {
		return customer_Service.forgot_passowrd(email);
	}

	@PostMapping("/forgot-otp/{email}")
	public ResponseEntity<ResponseStructure<Customer>> submitForgotOtp(@PathVariable String email,
			@RequestParam int otp) {
		return customer_Service.submitForgotOtp(email, otp);
	}

	@PostMapping("/reset-password")
	public ResponseEntity<ResponseStructure<Customer>> setpassword(@RequestParam String email,
			@RequestParam String password, @RequestHeader("Bearer") String token) {
		return customer_Service.setPassword(email, password, token);
	}

	@GetMapping("/searchStation")
	public ResponseEntity<ResponseStructure<List<Train>>> searchstation(@RequestParam String start,
			@RequestParam String end, @RequestParam String email, @RequestHeader("Bearer") String token,
			@RequestParam String date) {
		return customer_Service.searchStation(start, end, email, token, date);
	}

	@GetMapping("/select_class/{train_no}")
	public ResponseEntity<ResponseStructure<Seat>> selectSeatType(@RequestParam String seat_type,
			@PathVariable int train_no, @RequestHeader("Bearer") String token, @RequestParam String date,
			@RequestParam String start, @RequestParam String end) {
		return customer_Service.selectSeatType(seat_type, train_no, token, date, start, end);
	}

	@PostMapping("/booking/{train_no}")
	public ResponseEntity<ResponseStructure<Booking>> booking(@RequestBody List<Booking> bookings,
			@RequestHeader("Bearer") String token, @PathVariable int train_no, @RequestParam String seat_type) {
		return customer_Service.booking(bookings, token, train_no, seat_type);
	}

}