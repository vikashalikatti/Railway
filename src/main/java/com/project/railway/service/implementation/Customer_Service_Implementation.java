package com.project.railway.service.implementation;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.project.railway.configuration.Twilio_Configuration;
import com.project.railway.dto.Customer;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.repository.Customer_Repository;
import com.project.railway.service.Customer_Service;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;
import jakarta.mail.internet.ParseException;

@Service
public class Customer_Service_Implementation implements Customer_Service {

	@Autowired
	private Customer_Repository customer_Repository;

	@Autowired
	private Twilio_Configuration configuration;

	@Autowired
	private BCryptPasswordEncoder encoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	JwtUtil jwtUtil;

	public ResponseEntity<ResponseStructure<Customer>> signup(Customer customer, MultipartFile pic)
			throws IOException, ParseException, TemplateException {

		ResponseStructure<Customer> structure = new ResponseStructure<>();

		customer.setPassword(encoder.encode(customer.getPassword()));
		byte[] picture = new byte[pic.getBytes().length];
		pic.getInputStream().read(picture);
		customer.setPhoto(picture);

		// Check for duplicate email or mobile
		if (customer_Repository.findByEmail(customer.getEmail()) != null
				|| customer_Repository.findByMobile(customer.getMobile()) != null) {
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			structure.setMessage("Email or Mobile should not be repeated");
			return ResponseEntity.badRequest().body(structure);
		} else {
			int otp = new Random().nextInt(100000, 999999);
			customer.setOtp(otp);
			customer.setSetOtpGeneratedTime(LocalDateTime.now());
			String mobile = "+" + String.valueOf(customer.getMobile());

			try {
				Twilio.init(configuration.getAccountSid(), configuration.getAuthToken());

				Message message = Message
						.creator(new PhoneNumber(mobile), new PhoneNumber(configuration.getTrailNumber()), 
								"Your OTP is: " + otp)
						.create();

				// Check if SMS was sent successfully
				if (message.getStatus() == Message.Status.SENT) {
					// Save customer details with OTP in the repository
					customer_Repository.save(customer);

					structure.setData2(customer);
					structure.setStatus(HttpStatus.CREATED.value());
					structure.setMessage("OTP sent successfully via SMS");

					return ResponseEntity.ok(structure);
				} else {
					structure.setData(null);
					structure.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
					structure.setMessage("Failed to send OTP via SMS. Twilio status: " + message.getStatus());
					return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(structure);
				}
			} catch (Exception e) {
				// Log or print the exception for debugging
				e.printStackTrace();

				structure.setData(null);
				structure.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
				structure.setMessage("Error sending OTP via SMS. Exception: " + e.getMessage());
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(structure);
			}
		}
	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> login(String email, String password)
			throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException,
			TemplateException {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		Customer customer = customer_Repository.findByEmail(email);

		UsernamePasswordAuthenticationToken passwordAuthenticationToken = new UsernamePasswordAuthenticationToken(email,
				password);

		Authentication authentication = authenticationManager.authenticate(passwordAuthenticationToken);

		SecurityContextHolder.getContext().setAuthentication(authentication);

		UserDetails details = (UserDetails) authentication.getPrincipal();

		if (customer != null) {
			long expirationMills = System.currentTimeMillis() + 3600000;
			Date expirationDate = new Date(expirationMills);
//			mail.sendOtp(customer);
			String token = jwtUtil.generateToken_for_admin(details, expirationDate);
			structure.setData(token);
			structure.setMessage("Login Success");
			structure.setStatus(HttpStatus.OK.value());
			return new ResponseEntity<>(structure, HttpStatus.OK);
		} else {
			structure.setData(null);
			structure.setMessage("NO Data found,Create New Account To Login");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);

		}

	}

}
