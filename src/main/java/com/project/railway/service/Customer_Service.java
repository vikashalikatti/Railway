package com.project.railway.service;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.project.railway.dto.Customer;
import com.project.railway.dto.Seat;
import com.project.railway.dto.Train;
import com.project.railway.helper.ResponseStructure;

import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateNotFoundException;
import jakarta.mail.internet.ParseException;

public interface Customer_Service {

	ResponseEntity<ResponseStructure<Customer>> signup(Customer customer, MultipartFile pic) throws Exception;

	ResponseEntity<ResponseStructure<Customer>> login(String email, String password)
			throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException;

	ResponseEntity<ResponseStructure<Customer>> verifyotp(String email, int otp);

	ResponseEntity<ResponseStructure<Customer>> forgot_passowrd(String email) throws Exception;

	ResponseEntity<ResponseStructure<Customer>> submitForgotOtp(String email, int otp);

	ResponseEntity<ResponseStructure<Customer>> setPassword(String email, String password, String token);

	ResponseEntity<ResponseStructure<Train>> searchStation(String start, String end, String email, String token,
			String date);

	ResponseEntity<ResponseStructure<Seat>> selectSeatType(String seat_type, int train_no, String token, String date);

}
