package com.project.railway.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.railway.dto.Availability;
import com.project.railway.dto.Booking;
import com.project.railway.dto.PassengerRequest;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.service.BookingService;
import com.project.railway.service.StationDirectory;

/** Seat availability, booking, PNR status and cancellation for passengers */
@RestController
@RequestMapping("customer")
public class Booking_Controller {

	@Autowired
	private BookingService bookingService;

	/** Live station search: ?q=SBC, ?q=beng, ?q=Hubballi. No log-in needed. */
	@GetMapping("/stations")
	public ResponseEntity<ResponseStructure<List<StationDirectory.Suggestion>>> stations(@RequestParam(defaultValue = "") String q) {
		return bookingService.stations(q);
	}

	/** Trains between two places: from / to are a station code, city or station name; date is M/d/yyyy */
	@GetMapping("/trains")
	public ResponseEntity<ResponseStructure<List<BookingService.TrainMatch>>> searchTrains(
			@RequestHeader("Bearer") String token, @RequestParam String from, @RequestParam String to,
			@RequestParam String date) {
		return bookingService.searchTrains(token, from, to, date);
	}

	/** Seats left in one class between two stations on a date */
	@GetMapping("/select_class/{train_no}/{start}/{end}")
	public ResponseEntity<ResponseStructure<Availability>> availability(@RequestParam String seat_type,
			@PathVariable int train_no, @RequestHeader("Bearer") String token, @RequestParam String date,
			@PathVariable String start, @PathVariable String end,
			@RequestParam(defaultValue = "GN") String quota) {
		return bookingService.availability(token, train_no, seat_type, date, start, end, quota);
	}

	/**
	 * Books up to 6 passengers. Body: [{"name":"Vikas","age":25,"gender":"Male","berthPreference":"LOWER"}]
	 * book_date is M/d/yyyy; from / to are station names on the train's route;
	 * quota is GN (default), TQ (Tatkal), PT (Premium Tatkal), LD (Ladies) or SS (Senior citizen).
	 */
	@PostMapping("/booking/{train_no}")
	public ResponseEntity<ResponseStructure<Booking>> book(@RequestBody List<PassengerRequest> passengers,
			@RequestHeader("Bearer") String token, @PathVariable int train_no, @RequestParam String seat_type,
			@RequestParam String book_date, @RequestParam String from, @RequestParam String to,
			@RequestParam(defaultValue = "GN") String quota) {
		return bookingService.book(token, train_no, seat_type, book_date, from, to, quota, passengers);
	}

	@GetMapping("/pnr/{pnr}")
	public ResponseEntity<ResponseStructure<Booking>> pnrStatus(@RequestHeader("Bearer") String token,
			@PathVariable long pnr) {
		return bookingService.pnrStatus(token, pnr);
	}

	@GetMapping("/my_bookings")
	public ResponseEntity<ResponseStructure<List<Booking>>> myBookings(@RequestHeader("Bearer") String token) {
		return bookingService.myBookings(token);
	}

	/** Cancels the listed passenger ids, or the whole booking when the body is empty */
	@PostMapping("/cancel/{pnr}")
	public ResponseEntity<ResponseStructure<Booking>> cancel(@RequestHeader("Bearer") String token,
			@PathVariable long pnr, @RequestBody(required = false) List<Long> passengerIds) {
		return bookingService.cancel(token, pnr, passengerIds);
	}
}
