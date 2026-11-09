package com.project.railway.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.railway.dto.Admin;
import com.project.railway.dto.ChartView;
import com.project.railway.dto.QuotaView;
import com.project.railway.dto.Route;
import com.project.railway.dto.Schedule;
import com.project.railway.dto.Station;
import com.project.railway.dto.StationMaster;
import com.project.railway.dto.Train;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.service.Admin_Service;

import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateNotFoundException;

@RestController
@RequestMapping("admin")
public class Admin_Controller {

	@org.springframework.beans.factory.annotation.Autowired
	private com.project.railway.service.ChartService chartService;

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

	@PostMapping("/schedule")
	public ResponseEntity<ResponseStructure<Schedule>> addSchedule(@ModelAttribute Schedule schedule,
			@RequestHeader("Bearer") String token, @RequestParam int train_No) {
		return admin_Service.addSchedule(schedule, token, train_No);
	}

	@PostMapping("/addstations/{train_No}")
	public ResponseEntity<ResponseStructure<Train>> addStation(@RequestBody List<Station> station,
			@RequestHeader("Bearer") String token, @PathVariable int train_No) {
		return admin_Service.addStation(station, token, train_No);
	}

	@PostMapping("/addRoute/{train_No}")
	public ResponseEntity<ResponseStructure<Train>> addRoutesWithPrices(@RequestBody Route route,
			@RequestHeader("Bearer") String token, @PathVariable int train_No) {
		return admin_Service.addRoutesWithPrices(route, token, train_No);
	}

	/** Body: {"SLEEPER_CLASS": 10, "AC3_TIER": 4, "AC2_TIER": 2, "AC1_TIER": 1, "SECOND_CLASS": 2}.
	 *  Also AC_CHAIR_CAR (CC), EXECUTIVE_CHAIR_CAR (EC) and AC3_ECONOMY (3E). */
	@PostMapping("/addCoaches/{train_No}")
	public ResponseEntity<ResponseStructure<Train>> addCoaches(@RequestBody Map<String, Integer> composition,
			@RequestHeader("Bearer") String token, @PathVariable int train_No) {
		return admin_Service.addCoaches(composition, token, train_No);
	}

	/** Quota split per class of a train: Tatkal, Premium Tatkal, Ladies and Senior citizen in percent */
	@GetMapping("/quotas/{train_No}")
	public ResponseEntity<ResponseStructure<List<QuotaView>>> quotas(@RequestHeader("Bearer") String token,
			@PathVariable int train_No) {
		return admin_Service.quotas(token, train_No);
	}

	/** Body: {"SLEEPER_CLASS": {"tatkalPct": 30, "premiumPct": 10, "ladiesPct": 5, "seniorPct": 8}} */
	@PostMapping("/quotas/{train_No}")
	public ResponseEntity<ResponseStructure<List<QuotaView>>> saveQuotas(@RequestHeader("Bearer") String token,
			@PathVariable int train_No, @RequestBody Map<String, Map<String, Integer>> body) {
		return admin_Service.saveQuotas(token, train_No, body);
	}

	/** Reservation chart with every passenger (name, age, gender, PNR, berth). Any time. date is yyyy-MM-dd. */
	@GetMapping("/chart/{train_No}")
	public ResponseEntity<ResponseStructure<ChartView>> chart(@RequestHeader("Bearer") String token,
			@PathVariable int train_No, @RequestParam String date) {
		return chartService.adminChart(token, train_No, date);
	}

	/**
	 * Adds coaches from a date, for good or until an end date (rush days).
	 * Body: {"seatType": "SLEEPER_CLASS", "count": 2, "from": "2026-11-12", "to": "2026-11-20"}
	 */
	@PostMapping("/coaches/{train_No}/add")
	public ResponseEntity<ResponseStructure<Train>> addCoachesFrom(@RequestHeader("Bearer") String token,
			@PathVariable int train_No, @RequestBody Map<String, Object> body) {
		return admin_Service.addCoachesFrom(token, train_No, body);
	}

	/** Takes coaches off from a date on. Body: {"coachNumbers": ["S9", "S10"], "from": "2026-11-21"} */
	@PostMapping("/coaches/{train_No}/remove")
	public ResponseEntity<ResponseStructure<Train>> removeCoachesFrom(@RequestHeader("Bearer") String token,
			@PathVariable int train_No, @RequestBody Map<String, Object> body) {
		return admin_Service.removeCoachesFrom(token, train_No, body);
	}

	/** All trains, for the admin page */
	@GetMapping("/trains")
	public ResponseEntity<ResponseStructure<List<Train>>> listTrains(@RequestHeader("Bearer") String token) {
		return admin_Service.listTrains(token);
	}

	/** Adds a station to the directory. Body: {"code":"SBC","name":"KSR Bengaluru City Jn","city":"Bengaluru","state":"Karnataka"} */
	@PostMapping("/stations")
	public ResponseEntity<ResponseStructure<StationMaster>> addStation(@RequestBody StationMaster station,
			@RequestHeader("Bearer") String token) {
		return admin_Service.addStationMaster(station, token);
	}

	/**
	 * Estimated km between consecutive stops, e.g. ?codes=SBC,TK,DVG,UBL -> [null, 80, 224, 160].
	 * Straight-line distance x 1.25; null where a station has no map position.
	 */
	@GetMapping("/distances")
	public ResponseEntity<ResponseStructure<List<Integer>>> distances(@RequestParam List<String> codes,
			@RequestHeader("Bearer") String token) {
		return admin_Service.distances(codes, token);
	}

	/** Logs out this session; everywhere=true logs out every device */
	@PostMapping("/logout")
	public ResponseEntity<ResponseStructure<Admin>> logout(@RequestHeader("Bearer") String token,
			@RequestParam(defaultValue = "false") boolean everywhere) {
		return admin_Service.logout(token, everywhere);
	}

	@DeleteMapping("/delete/{train_No}")
	public ResponseEntity<ResponseStructure<Train>> delete_train(@PathVariable int train_No,
			@RequestHeader("Bearer") String token) {
		return admin_Service.delete(train_No,token);
	}

}
