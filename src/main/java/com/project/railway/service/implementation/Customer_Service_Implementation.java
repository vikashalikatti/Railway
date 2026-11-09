package com.project.railway.service.implementation;

import java.io.IOException;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

import com.project.railway.dto.Customer;
import com.project.railway.dto.Schedule;
import com.project.railway.dto.Station;
import com.project.railway.dto.Train;
import com.project.railway.helper.AccountRules;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.LoginAttemptService;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.helper.Seat_type;
import com.project.railway.helper.EmailOtpService;
import com.project.railway.repository.Book_Repository;
import com.project.railway.repository.Customer_Repository;
import com.project.railway.repository.Station_Repository;
import com.project.railway.repository.Train_Repository;
import com.project.railway.service.Customer_Service;

import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateNotFoundException;
import jakarta.mail.internet.ParseException;

@Service
public  class Customer_Service_Implementation implements Customer_Service {

	@Autowired
	private Customer_Repository customer_Repository;

	@Autowired
	private BCryptPasswordEncoder encoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	JwtUtil jwtUtil;

	@Autowired
	EmailOtpService emailOtpService;

	@Autowired
	Train_Repository train_Repository;

	@Autowired
	Station_Repository station_Repository;
	
	@Autowired
	Book_Repository booking_Repository ;
	


	/** A real BCrypt hash, checked when the account doesn't exist so a wrong email takes as long as a wrong password */
	private volatile String dummyHash;

	@Autowired
	private LoginAttemptService attempts;

	@Override
	public ResponseEntity<ResponseStructure<Customer>> signup(Customer customer, MultipartFile pic) throws Exception {
		ResponseStructure<Customer> structure = new ResponseStructure<>();

		// Same checks as the sign-up form, for requests that don't come from it
		String problem = AccountRules.personName(customer.getName());
		if (problem == null) problem = AccountRules.email(customer.getEmail());
		if (problem == null) problem = AccountRules.newPassword(customer.getPassword());
		if (problem == null) problem = AccountRules.mobile(customer.getMobile());
		if (problem == null) problem = AccountRules.age(customer.getAge());
		if (problem == null) problem = AccountRules.gender(customer.getGender());
		if (problem == null) problem = AccountRules.address(customer.getAddress());
		if (problem == null) problem = AccountRules.photo(pic);
		if (problem != null) {
			return reply(structure, HttpStatus.BAD_REQUEST, problem);
		}
		String email = customer.getEmail().trim();
		if (customer_Repository.findByEmail(email) != null || customer_Repository.findByMobile(customer.getMobile()) != null) {
			return reply(structure, HttpStatus.BAD_REQUEST, "Email or Mobile should not be repeated");
		}

		// Only the fields a person may choose are kept; everything else is set here, so a request
		// can't pick its own id (overwriting another account), role, verified flag or OTP
		Customer account = new Customer();
		account.setName(customer.getName().trim());
		account.setEmail(email);
		account.setPassword(encoder.encode(customer.getPassword()));
		account.setMobile(customer.getMobile());
		account.setAge(customer.getAge());
		account.setGender(customer.getGender().trim());
		account.setAddress(customer.getAddress().trim());
		account.setPhoto(pic.getBytes());
		account.setStatus(false); // becomes true once the email OTP is verified
		account.setRole("customer");
		account.setTokenVersion(0);
		account.setOtpAttempts(0);

		attempts.emailCooldown(email);
		if (emailOtpService.sendOtp(account, EmailOtpService.Purpose.SIGNUP)) {
			customer_Repository.save(account);
			// The account (which holds the OTP) is not sent back, so the code can only come from the email
			structure.setStatus(HttpStatus.CREATED.value());
			structure.setMessage("OTP sent to " + email);
			return ResponseEntity.ok(structure);
		}
		return reply(structure, HttpStatus.INTERNAL_SERVER_ERROR,
				"Couldn't send the OTP email. Check the email address and try again.");
	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> login(String email, String password)
			throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		String account = "customer:" + (email == null ? "" : email.trim());
		long wait = attempts.secondsLocked(account);
		if (wait > 0) {
			return reply(structure, HttpStatus.TOO_MANY_REQUESTS, LoginAttemptService.waitMessage(wait));
		}
		Customer customer = email == null ? null : customer_Repository.findByEmail(email.trim());
		boolean ok = customer != null && password != null && encoder.matches(password, customer.getPassword());
		if (customer == null && password != null) {
			if (dummyHash == null) {
				dummyHash = encoder.encode("no-such-account-0");
			}
			encoder.matches(password, dummyHash); // same delay either way
		}
		if (!ok) {
			attempts.failed(account);
			return reply(structure, HttpStatus.BAD_REQUEST, "Wrong email or password");
		}
		attempts.succeeded(account);
		if (!customer.isStatus()) {
			return reply(structure, HttpStatus.BAD_REQUEST,
					"Email not verified. Enter the OTP we emailed you, or request a new one.");
		}
		if (Boolean.TRUE.equals(customer.getBlocked())) {
			return reply(structure, HttpStatus.FORBIDDEN, "Your account is blocked. Contact support.");
		}
		String token = jwtUtil.generateToken_for_customer(customer,
				new java.util.Date(System.currentTimeMillis() + JwtUtil.LOGIN_TTL_MS));
		structure.setData(token);
		return reply(structure, HttpStatus.OK, "Login Success");
	}

	/** Ends this session, or every session of this passenger when everywhere is true */
	@Override
	public ResponseEntity<ResponseStructure<Customer>> logout(String token, boolean everywhere) {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		String[] subject = jwtUtil.getSubject(token);
		if (subject != null && everywhere) {
			Customer customer = customer_Repository.findByEmail(subject[0]);
			if (customer != null) {
				customer.setTokenVersion(JwtUtil.version(customer.getTokenVersion()) + 1);
				customer_Repository.save(customer);
			}
		}
		jwtUtil.logout(token);
		return reply(structure, HttpStatus.OK, everywhere ? "Logged out on every device" : "Logged out");
	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> verifyotp(String email, int otp) {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		String problem = checkOtp(email, otp);
		if (problem != null) {
			return reply(structure, problem.startsWith("Too many") ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST,
					problem);
		}
		Customer customer = customer_Repository.findByEmail(email.trim());
		customer.setStatus(true);
		customer_Repository.save(customer);
		return reply(structure, HttpStatus.OK, "OTP Verified Successfully");
	}

	/**
	 * Checks an OTP and uses it up when it is right. Returns null when it is right, or why not.
	 * 5 wrong codes cancel the OTP (a new one has to be requested), and the account's OTP checks
	 * are locked for 15 minutes, so the 6-digit code can't be guessed.
	 */
	private String checkOtp(String email, int otp) {
		String account = "otp:" + (email == null ? "" : email.trim());
		long wait = attempts.secondsLocked(account);
		if (wait > 0) {
			return LoginAttemptService.waitMessage(wait);
		}
		Customer customer = email == null ? null : customer_Repository.findByEmail(email.trim());
		if (customer == null || customer.getOtp() == 0) {
			attempts.failed(account);
			return "Incorrect OTP. Request a new one if you don't have it.";
		}
		if (!emailOtpService.isOtpValid(customer)) {
			customer.setOtp(0);
			customer_Repository.save(customer);
			return "OTP has expired.";
		}
		if (customer.getOtp() != otp) {
			attempts.failed(account);
			int tries = (customer.getOtpAttempts() == null ? 0 : customer.getOtpAttempts()) + 1;
			customer.setOtpAttempts(tries);
			if (tries >= 5) {
				customer.setOtp(0); // this code can't be guessed any further
				customer.setOtpAttempts(0);
				customer_Repository.save(customer);
				return "Incorrect OTP entered too many times. Request a new OTP.";
			}
			customer_Repository.save(customer);
			return "Incorrect OTP. " + (5 - tries) + (5 - tries == 1 ? " try" : " tries") + " left.";
		}
		attempts.succeeded(account);
		customer.setOtp(0);
		customer.setOtpAttempts(0);
		customer.setSetOtpGeneratedTime(null);
		customer_Repository.save(customer);
		return null;
	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> forgot_passowrd(String email) throws Exception {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		Customer customer = email == null ? null : customer_Repository.findByEmail(email.trim());
		if (customer == null) {
			return reply(structure, HttpStatus.BAD_REQUEST, "Email doesn't exist, create an account first");
		}
		long wait = attempts.emailCooldown(customer.getEmail());
		if (wait > 0) {
			return reply(structure, HttpStatus.TOO_MANY_REQUESTS,
					"An OTP was just sent. Wait " + wait + " seconds before asking for another.");
		}
		customer.setOtpAttempts(0);
		if (emailOtpService.sendOtp(customer, EmailOtpService.Purpose.RESET_PASSWORD)) {
			customer_Repository.save(customer);
			return reply(structure, HttpStatus.OK, "OTP sent to " + customer.getEmail());
		}
		return reply(structure, HttpStatus.NOT_ACCEPTABLE,
				"Couldn't send the OTP email. Check the email address and try again.");
	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> resendOtp(String email) {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		Customer customer = email == null ? null : customer_Repository.findByEmail(email.trim());
		if (customer == null) {
			return reply(structure, HttpStatus.BAD_REQUEST, "Email doesn't exist, create an account first");
		}
		if (customer.isStatus()) {
			return reply(structure, HttpStatus.BAD_REQUEST, "This email is already verified. You can log in.");
		}
		long wait = attempts.emailCooldown(customer.getEmail());
		if (wait > 0) {
			return reply(structure, HttpStatus.TOO_MANY_REQUESTS,
					"An OTP was just sent. Wait " + wait + " seconds before asking for another.");
		}
		customer.setOtpAttempts(0);
		if (emailOtpService.sendOtp(customer, EmailOtpService.Purpose.SIGNUP)) {
			customer_Repository.save(customer);
			return reply(structure, HttpStatus.OK, "New OTP sent to " + customer.getEmail());
		}
		return reply(structure, HttpStatus.INTERNAL_SERVER_ERROR, "Couldn't send the OTP email. Try again.");
	}

	/** Right OTP -> a 10-minute token that can only be used to set a new password */
	@Override
	public ResponseEntity<ResponseStructure<Customer>> submitForgotOtp(String email, int otp) {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		String problem = checkOtp(email, otp);
		if (problem != null) {
			return reply(structure, problem.startsWith("Too many") ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST,
					problem);
		}
		Customer customer = customer_Repository.findByEmail(email.trim());
		customer.setStatus(true); // they proved they own the email
		customer_Repository.save(customer);
		structure.setData(jwtUtil.generateResetToken(customer));
		return reply(structure, HttpStatus.ACCEPTED, "Account Verified Successfully");
	}

	/**
	 * Sets a new password. Needs the reset token from submitForgotOtp; the account comes from
	 * that token, never from the request. Every other session is logged out.
	 */
	@Override
	public ResponseEntity<ResponseStructure<Customer>> setPassword(String email, String password, String token) {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		JwtUtil.TokenInfo info = jwtUtil.verify(token, JwtUtil.PURPOSE_RESET);
		if (info == null) {
			return reply(structure, HttpStatus.UNAUTHORIZED,
					"This reset link has expired. Ask for a new OTP and try again.");
		}
		if (email != null && !email.isBlank() && !email.trim().equalsIgnoreCase(info.subject())) {
			return reply(structure, HttpStatus.BAD_REQUEST, "This reset is for a different email address.");
		}
		String problem = AccountRules.newPassword(password);
		if (problem != null) {
			return reply(structure, HttpStatus.BAD_REQUEST, problem);
		}
		Customer customer = customer_Repository.findByEmail(info.subject());
		customer.setPassword(encoder.encode(password));
		// ends every session, and this reset token too
		customer.setTokenVersion(JwtUtil.version(customer.getTokenVersion()) + 1);
		customer_Repository.save(customer);
		return reply(structure, HttpStatus.OK, "Password Reset Success");
	}

	private static <T> ResponseEntity<ResponseStructure<T>> reply(ResponseStructure<T> structure, HttpStatus status,
			String message) {
		structure.setMessage(message);
		structure.setStatus(status.value());
		return new ResponseEntity<>(structure, status);
	}

	@Override
	public ResponseEntity<ResponseStructure<List<Train>>> searchStation(String start, String end, String email,
			String token, String date) {
		ResponseStructure<List<Train>> structure = new ResponseStructure<>();
		// The passenger comes from the token, not from the email in the request
		String[] subject = jwtUtil.getSubject(token);
		Customer customer = subject == null ? null : customer_Repository.findByEmail(subject[0]);

		if (!jwtUtil.isValidToken(token)) {
			structure.setMessage("Invalid token.");
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		} else {
			if (customer != null) {
				List<Station> boardingStations = station_Repository.findByStationName(start);
				List<Station> destinationStations = station_Repository.findByStationName(end);

				if (boardingStations.isEmpty() || destinationStations.isEmpty()) {
					structure.setMessage("No matching stations found.");
					structure.setStatus(HttpStatus.NOT_FOUND.value());
					return new ResponseEntity<>(structure, HttpStatus.NOT_FOUND);
				} else {
					List<Train> matchingTrains = new ArrayList<>();

					for (Station station : boardingStations) {
						Train train = station.getTrains();
						List<Station> stations = train.getStations();
						boolean isRunningOnRoute = isTrainRunningOnRoute(stations, start, end);

						DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy");
						LocalDate localDate = LocalDate.parse(date, formatter);
						Schedule schedule = train.getSchedule();
						String[] week = schedule.getRunningWeeks();
						String dayOfWeek = localDate.getDayOfWeek().toString().toLowerCase();

						if (localDate.isBefore(LocalDate.now())) {
							structure.setMessage("Specified date is in the past.");
							structure.setStatus(HttpStatus.BAD_REQUEST.value());
							return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
						}

						if (isRunningOnRoute
								&& Arrays.stream(week).map(String::toLowerCase).anyMatch(dayOfWeek::equals)) {
							matchingTrains.add(train);
						}
					}

					if (!matchingTrains.isEmpty()) {
						structure.setData2(matchingTrains);
						structure.setMessage("List Of Trains " + start + " to " + end);
						structure.setStatus(HttpStatus.OK.value());
						return new ResponseEntity<>(structure, HttpStatus.OK);
					} else {
						structure.setMessage("No matching trains found for the specified route.");
						structure.setStatus(HttpStatus.NOT_FOUND.value());
						return new ResponseEntity<>(structure, HttpStatus.NOT_FOUND);
					}
				}
			} else {
				structure.setMessage("Customer not found.");
				structure.setStatus(HttpStatus.NOT_FOUND.value());
				return new ResponseEntity<>(structure, HttpStatus.NOT_FOUND);
			}
		}
	}

	private boolean isTrainRunningOnRoute(List<Station> stations, String start, String end) {
		boolean foundStart = false;

		for (Station station : stations) {
			if (station.getStationName().contains(start)) {
				foundStart = true;
			} else if (foundStart && station.getStationName().contains(end)) {
				return true;
			}
		}

		return false;
	}
}
