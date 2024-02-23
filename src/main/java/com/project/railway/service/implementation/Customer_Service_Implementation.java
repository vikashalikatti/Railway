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

import com.project.railway.dto.Booking;
import com.project.railway.dto.Customer;
import com.project.railway.dto.Schedule;
import com.project.railway.dto.Seat;
import com.project.railway.dto.Station;
import com.project.railway.dto.Train;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.helper.Seat_type;
import com.project.railway.helper.Sms_Service;
import com.project.railway.repository.Customer_Repository;
import com.project.railway.repository.Station_Repository;
import com.project.railway.repository.Train_Repository;
import com.project.railway.service.Customer_Service;

import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateNotFoundException;
import jakarta.mail.internet.ParseException;

@Service
public class Customer_Service_Implementation implements Customer_Service {

	@Autowired
	private Customer_Repository customer_Repository;

	@Autowired
	private BCryptPasswordEncoder encoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	JwtUtil jwtUtil;

	@Autowired
	Sms_Service sms_Service;

	@Autowired
	Train_Repository train_Repository;

	@Autowired
	Station_Repository station_Repository;

	public ResponseEntity<ResponseStructure<Customer>> signup(Customer customer, MultipartFile pic) throws Exception {

		ResponseStructure<Customer> structure = new ResponseStructure<>();

		customer.setPassword(encoder.encode(customer.getPassword()));
		byte[] picture = new byte[pic.getBytes().length];
		pic.getInputStream().read(picture);
		customer.setPhoto(picture);
		if (customer_Repository.findByEmail(customer.getEmail()) != null
				|| customer_Repository.findByMobile(customer.getMobile()) != null) {
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			structure.setMessage("Email or Mobile should not be repeated");
			return ResponseEntity.badRequest().body(structure);
		} else {
			boolean sms = sms_Service.smsSent(customer);
			if (sms) {
				customer.setStatus(true);
				customer.setRole("customer");
				customer_Repository.save(customer);

				structure.setData2(customer);
				structure.setStatus(HttpStatus.CREATED.value());
				structure.setMessage("OTP sent successfully via SMS");

				return ResponseEntity.ok(structure);
			} else {
				structure.setData(null);
				structure.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
				structure.setMessage("Failed to send OTP via SMS");
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(structure);

			}
		}
	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> login(String email, String password)
			throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		Customer customer = customer_Repository.findByEmail(email);
		if (customer == null) {
			structure.setData(null);
			structure.setMessage("No User, Create Your Account");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		} else {

			UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(email, password);

			Authentication authentication = authenticationManager.authenticate(authToken);
			SecurityContextHolder.getContext().setAuthentication(authentication);

			UserDetails userDetails = (UserDetails) authentication.getPrincipal();

			if (userDetails != null) {
				long expirationMillis = System.currentTimeMillis() + 3600000; // 1 hour in milliseconds
				Date expirationDate = new Date(expirationMillis);
				String token = jwtUtil.generateToken_for_customer(customer, expirationDate);
				structure.setData(token);
				structure.setMessage("Login Success");
				structure.setStatus(HttpStatus.OK.value());
			}
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		}
	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> verifyotp(String email, int otp) {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		Customer customer = customer_Repository.findByEmail(email);
		if (customer != null && customer.getOtp() == otp) {
			if (sms_Service.isOtpValid(customer)) {
				customer.setStatus(true);
				customer.setOtp(0);
				customer.setSetOtpGeneratedTime(null);
				customer_Repository.save(customer);
				structure.setData2(customer);
				structure.setStatus(HttpStatus.OK.value());
				structure.setMessage("OTP Verified Successfully");
				return new ResponseEntity<>(structure, HttpStatus.OK);
			} else {
				structure.setData(null);
				structure.setStatus(HttpStatus.BAD_REQUEST.value());
				structure.setMessage("OTP has expired.");
				return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
			}
		} else {
			structure.setData(null);
			structure.setMessage("Otp Not Verified Sucessfully");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		}
	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> forgot_passowrd(String email) throws Exception {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		Customer customer = customer_Repository.findByEmail(email);
		if (customer == null) {
			structure.setData2(customer);
			structure.setMessage(customer.getEmail() + "Email doesn't exits,create account first");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		} else {
			int otp = new Random().nextInt(100000, 999999);
			customer.setOtp(otp);
			customer.setSetOtpGeneratedTime(LocalDateTime.now());

			if (sms_Service.smsSent(customer)) {
				customer_Repository.save(customer);
				structure.setData2(customer);
				structure.setStatus(HttpStatus.OK.value());
				structure.setMessage(customer.getEmail() + "OTP send succesfull,check once");
				return new ResponseEntity<>(structure, HttpStatus.OK);
			} else {
				structure.setData(null);
				structure.setStatus(HttpStatus.NOT_ACCEPTABLE.value());
				structure.setMessage("Something went Wrong, Check email and try again");
				return new ResponseEntity<>(structure, HttpStatus.NOT_ACCEPTABLE);
			}
		}

	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> submitForgotOtp(String email, int otp) {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		Customer customer = customer_Repository.findByEmail(email);

		if (customer != null && customer.getOtp() == otp) {
			LocalDateTime otpGeneratedTime = customer.getSetOtpGeneratedTime();
			LocalDateTime currentTime = LocalDateTime.now();
			Duration duration = Duration.between(otpGeneratedTime, currentTime);

			if (duration.toMinutes() <= 5) {
				customer.setStatus(true);
				customer.setOtp(0);
				long expirationMillis = System.currentTimeMillis() + 600000; // 10 min in milliseconds
				Date expirationDate = new Date(expirationMillis);
				String token = jwtUtil.generateToken_for_customer(customer, expirationDate);
				customer_Repository.save(customer);
				structure.setData(token);
				structure.setData2(customer);
				structure.setMessage("Account Verified Successfully");
				structure.setStatus(HttpStatus.ACCEPTED.value());
			} else {
				structure.setData(null);
				structure.setMessage("OTP has expired.");
				structure.setStatus(HttpStatus.BAD_REQUEST.value());
			}
		} else {
			structure.setData(null);
			structure.setMessage("Incorrect OTP");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
		}

		return new ResponseEntity<>(structure, HttpStatus.OK);
	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> setPassword(String email, String password, String token) {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		Customer customer = customer_Repository.findByEmail(email);
		if (!jwtUtil.isValidToken(token)) {
			structure.setData(null);
			structure.setMessage("Invalid or Expired Token, Please Login Again");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		} else {
			customer.setPassword(encoder.encode(password));
			customer_Repository.save(customer);
			structure.setData2(customer);
			structure.setMessage("Password Reset Success");
			structure.setStatus(HttpStatus.OK.value());
			return new ResponseEntity<>(structure, HttpStatus.OK);
		}
	}

	@Override
	public ResponseEntity<ResponseStructure<List<Train>>> searchStation(String start, String end, String email,
			String token, String date) {
		ResponseStructure<List<Train>> structure = new ResponseStructure<>();
		Customer customer = customer_Repository.findByEmail(email);

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

	@Override
	public ResponseEntity<ResponseStructure<Seat>> selectSeatType(String seatType, int trainNo, String token,
			String date, String start, String end) {
		ResponseStructure<Seat> structure = new ResponseStructure<>();
		Train train = train_Repository.findByTrainNumber(trainNo);

		if (!jwtUtil.isValidToken(token)) {
			structure.setMessage("Invalid token.");
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		}

		if (train == null) {
			structure.setMessage("Train not found");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		}

		List<Station> boardingStations = station_Repository.findByStationName(start);
		List<Station> destinationStations = station_Repository.findByStationName(end);

		if (boardingStations.isEmpty() || destinationStations.isEmpty()) {
			structure.setMessage("No matching stations found.");
			structure.setStatus(HttpStatus.NOT_FOUND.value());
			return new ResponseEntity<>(structure, HttpStatus.NOT_FOUND);
		}
		for (Station boardingStation : boardingStations) {
			LocalTime departureTime = boardingStation.getDepartureTime();
			LocalTime currentTime = LocalTime.now();

			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
			String formattedDepartureTime = departureTime.format(formatter);
			String formattedCurrentTime = currentTime.format(formatter);

			if (currentTime.isAfter(departureTime)) {
				structure.setMessage("Train Departed");
				structure.setStatus(HttpStatus.NOT_FOUND.value());
				return new ResponseEntity<>(structure, HttpStatus.NOT_FOUND);
			}
		}

		Seat seat = train.getSeat();
		seatType = seatType.toUpperCase();
		Seat_type inputSeatType;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy");
		LocalDate localDate = LocalDate.parse(date, formatter);

		if (localDate.isBefore(LocalDate.now())) {
			structure.setMessage("Specified date is in the past.");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		}

		Schedule schedule = train.getSchedule();
		String[] week = schedule.getRunningWeeks();
		String dayOfWeek = localDate.getDayOfWeek().toString().toLowerCase();

		boolean isRunningOnRoute = isTrainRunningOnRoute(train.getStations(), start, end);

		if (isRunningOnRoute && Arrays.stream(week).map(String::toLowerCase).anyMatch(dayOfWeek::equals)) {
			try {
				inputSeatType = Seat_type.valueOf(seatType);
			} catch (IllegalArgumentException e) {
				structure.setMessage("Invalid Seat Type");
				structure.setStatus(HttpStatus.BAD_REQUEST.value());
				return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
			}

			switch (inputSeatType) {
			case SECOND_CLASS:
			case SLEEPER_CLASS:
			case AC3_TIER:
			case AC2_TIER:
			case AC1_TIER:
				Seat seatNumber = getSeatNumberForType(seat, inputSeatType);
				structure.setData2(seatNumber);
				structure.setMessage("Seat Available");
				structure.setStatus(HttpStatus.OK.value());
				return new ResponseEntity<>(structure, HttpStatus.OK);
			default:
				structure.setMessage("No Seat Available");
				structure.setStatus(HttpStatus.BAD_REQUEST.value());
				return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
			}
		} else {
			structure.setMessage("No matching trains found for the specified route.");
			structure.setStatus(HttpStatus.NOT_FOUND.value());
			return new ResponseEntity<>(structure, HttpStatus.NOT_FOUND);
		}

	}

	private Seat getSeatNumberForType(Seat seat, Seat_type seatType) {
		Seat selectedSeat = new Seat();
		switch (seatType) {
		case SECOND_CLASS:
			selectedSeat.setSecond_class(seat.getSecond_class());
			break;
		case SLEEPER_CLASS:
			selectedSeat.setSleeper_class(seat.getSleeper_class());
			break;
		case AC3_TIER:
			selectedSeat.setAc3_tier(seat.getAc3_tier());
			break;
		case AC2_TIER:
			selectedSeat.setAc2_tier(seat.getAc2_tier());
			break;
		case AC1_TIER:
			selectedSeat.setAc1_tier(seat.getAc1_tier());
			break;
		}
		return selectedSeat;
	}

	@Override
	public ResponseEntity<ResponseStructure<Booking>> booking(List<Booking> bookings, String token, int train_no) {
		ResponseStructure<Booking> structure = new ResponseStructure<>();
		Train train = train_Repository.findByTrainNumber(train_no);
		if (!jwtUtil.isValidToken(token)) {
			structure.setMessage("Invalid token.");
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		} else {
			if (train != null) {
				for (Booking booking : bookings) {
					System.out.println("Name" + booking.getPassengerName());
					System.out.println("phone Number" + booking.getContactNumber());
				}
				structure.setMessage("booking done");
				structure.setStatus(HttpStatus.OK.value());
				return new ResponseEntity<>(structure, HttpStatus.OK);
			} else {
				structure.setMessage("Train Not Found");
				structure.setStatus(HttpStatus.BAD_REQUEST.value());
				return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
			}
		}
	}
}
