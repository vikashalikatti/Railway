package com.project.railway.service.implementation;

import java.io.IOException;
import java.net.InetAddress;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

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

import com.project.railway.dto.Admin;
import com.project.railway.dto.Route;
import com.project.railway.dto.Schedule;
import com.project.railway.dto.Seat;
import com.project.railway.dto.Station;
import com.project.railway.dto.Train;
import com.project.railway.helper.EmailService;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.repository.Admin_Repository;
import com.project.railway.repository.Route_Repository;
import com.project.railway.repository.Schedule_Repository;
import com.project.railway.repository.Station_Repository;
import com.project.railway.repository.Train_Repository;
import com.project.railway.service.Admin_Service;

import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateNotFoundException;

@Service
public class Admin_Service_Implementation implements Admin_Service {

	@Autowired
	Admin_Repository admin_Repository;

	@Autowired
	BCryptPasswordEncoder encoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	JwtUtil jwtUtil;

	@Autowired
	EmailService emailService;

	@Autowired
	Train_Repository train_Repository;

	@Autowired
	Schedule_Repository schedule_Repository;

	@Autowired
	Station_Repository station_Repository;

	@Autowired
	Route_Repository route_Repository;

	@Override
	public ResponseEntity<ResponseStructure<Admin>> create(Admin admin) {
		ResponseStructure<Admin> structure = new ResponseStructure<>();
		int existingEntries = admin_Repository.countByUsernameAndPassword(admin.getName(), admin.getPassword());
		if (existingEntries == 0) {
			admin.setPassword(encoder.encode(admin.getPassword()));
			admin.setRole("admin");
			admin_Repository.save(admin);
			structure.setData2(admin);
			structure.setMessage("Account Create for Admin");
			structure.setStatus(HttpStatus.CREATED.value());
			return new ResponseEntity<>(structure, HttpStatus.CREATED);
		} else {
			structure.setMessage("Admin Cannot More than one");
			structure.setStatus(HttpStatus.ALREADY_REPORTED.value());
			return new ResponseEntity<>(structure, HttpStatus.ALREADY_REPORTED);
		}
	}

	@Override
	public ResponseEntity<ResponseStructure<Admin>> login(String name, String password)
			throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException {
		ResponseStructure<Admin> structure = new ResponseStructure<>();
		Admin admin = admin_Repository.findByName(name);
		if (admin == null) {
			structure.setMessage("No User, Create Your Account");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		} else {

			UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(name, password);

			Authentication authentication = authenticationManager.authenticate(authToken);
			SecurityContextHolder.getContext().setAuthentication(authentication);

			UserDetails userDetails = (UserDetails) authentication.getPrincipal();

			if (userDetails != null) {
				String location = InetAddress.getLocalHost().getHostAddress();
				long expirationMillis = System.currentTimeMillis() + 3600000; // 1 hour in milliseconds
				Date expirationDate = new Date(expirationMillis);
//				emailService.sendInfoEmail(admin, location);
				String token = jwtUtil.generateToken_for_admin(userDetails, expirationDate);
				structure.setData(token);
				structure.setMessage("Login Success");
				structure.setStatus(HttpStatus.OK.value());
			}
			return new ResponseEntity<>(structure, HttpStatus.OK);
		}
	}

	@Override
	public ResponseEntity<ResponseStructure<Train>> trainadd(Train train, String token) {
		ResponseStructure<Train> structure = new ResponseStructure<>();
		if (!jwtUtil.isValidToken(token)) {
			structure.setMessage("Token Expired, Please Login Again");
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		} else {
			train_Repository.save(train);
			structure.setData2(train);
			structure.setMessage("trian");
			structure.setStatus(HttpStatus.OK.value());
			return new ResponseEntity<>(structure, HttpStatus.OK);
		}

	}

	@Override
	public ResponseEntity<ResponseStructure<Schedule>> addSchedule(Schedule schedule, String token, int trainNo) {
		ResponseStructure<Schedule> structure = new ResponseStructure<>();
		Train train = train_Repository.findByTrainNumber(trainNo);

		if (!jwtUtil.isValidToken(token)) {
			structure.setMessage("Token Expired, Please Login Again");
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		} else {
			if (train != null) {
				// Check if a schedule with the same information already exists for the train
				// number
				Schedule existingSchedule = schedule_Repository.findByTrainTrainNumber(trainNo);

				if (existingSchedule != null) {
					structure.setData2(null);
					structure.setMessage("Duplicate Schedule for the Train Number");
					structure.setStatus(HttpStatus.BAD_REQUEST.value());
					return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
				}

				schedule.setTrain(train);
				Schedule savedSchedule = schedule_Repository.save(schedule);
				train.setSchedule(savedSchedule);
				train_Repository.save(train);

				structure.setData2(savedSchedule);
				structure.setMessage("Schedule added successfully");
				structure.setStatus(HttpStatus.OK.value());
				return new ResponseEntity<>(structure, HttpStatus.OK);
			} else {
				structure.setData2(null);
				structure.setMessage("Train not found");
				structure.setStatus(HttpStatus.BAD_REQUEST.value());
				return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
			}
		}
	}

	@Override
	public ResponseEntity<ResponseStructure<Train>> addStation(List<Station> stations, String token, int trainNo) {
		ResponseStructure<Train> structure = new ResponseStructure<>();
		Train train = train_Repository.findByTrainNumber(trainNo);

		if (!jwtUtil.isValidToken(token)) {
			structure.setMessage("Token Expired, Please Login Again");
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		} else {
			List<Station> existingStations = train.getStations();
			if (existingStations == null) {
				existingStations = new ArrayList<>();
			}

			for (Station station : stations) {
				if (existingStations.stream()
						.anyMatch(existing -> existing.getStationName().equals(station.getStationName()))) {
					structure.setMessage("Duplicate Station");
					structure.setStatus(HttpStatus.BAD_REQUEST.value());
					return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
				}

				station.setTrains(train);
				existingStations.add(station);
			}

			train.setStations(existingStations);
			train_Repository.save(train);
			structure.setData2(train);
			structure.setMessage("Stations Added");
			structure.setStatus(HttpStatus.OK.value());
			return new ResponseEntity<>(structure, HttpStatus.OK);
		}
	}

	@Override
	public ResponseEntity<ResponseStructure<Train>> addRoutesWithPrices(Route routes, String token, int trainNo) {
		ResponseStructure<Train> structure = new ResponseStructure<>();
		Train train = train_Repository.findByTrainNumber(trainNo);

		if (!jwtUtil.isValidToken(token)) {
			structure.setMessage("Invalid or Expired Token, Please Login Again");
			structure.setStatus(HttpStatus.UNAUTHORIZED.value());
			return new ResponseEntity<>(structure, HttpStatus.UNAUTHORIZED);
		} else {
			String startStationName = routes.getStartStation();
			String endStationName = routes.getEndStation();

			if (startStationName == null || endStationName == null) {
				structure.setMessage("Invalid Station Names");
				structure.setStatus(HttpStatus.BAD_REQUEST.value());
				return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
			}

			double distance = routes.getDistance();
			double price = calculatePrice(distance);

			routes.setPrice(price);
			routes.setTrain(train);
			List<Station> stations = new ArrayList<>();
			stations.addAll(train.getStations());
			for (Station station : stations) {
				station.setRoute(routes);
			}
			routes.setStations(stations);
			route_Repository.save(routes);
			station_Repository.saveAll(stations);

			train.setRoute(routes);
			train_Repository.save(train);

			structure.setData2(train);
			structure.setMessage("Route with Prices Added to Every Station");
			structure.setStatus(HttpStatus.OK.value());
			return new ResponseEntity<>(structure, HttpStatus.OK);
		}
	}

	private double calculatePrice(double distance) {
		// Assuming a simple linear pricing model: price = distance * rate per kilometer
		double ratePerKilometer = 0.48; // Adjust this based on your pricing strategy
		return distance * ratePerKilometer;
	}

	@Override
	public ResponseEntity<ResponseStructure<Train>> addSeats(Seat seat, List<Route> route, String token, int trainNo) {
		return null;
//		ResponseStructure<Train> structure=new ResponseStructure<>();
//		Train train=train_Repository.findByTrainNumber(trainNo);
//
//		if(!jwtUtil.isValidToken(token)) {
//			structure.setData(null);
//			structure.setMessage("Invalid Token Please Login Again");
//			structure.setStatus(HttpStatus.BAD_REQUEST.value());
//			return new ResponseEntity<>(structure,HttpStatus.BAD_REQUEST);
//			
//		}

	}
}