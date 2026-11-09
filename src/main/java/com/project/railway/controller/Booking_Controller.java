package com.project.railway.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.railway.dto.Availability;
import com.project.railway.dto.ChartView;
import com.project.railway.dto.SavedPassenger;
import com.project.railway.dto.TravelViews;
import com.project.railway.dto.Booking;
import com.project.railway.dto.PassengerRequest;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.service.BookingService;
import com.project.railway.service.StationDirectory;

/** Seat availability, booking, PNR status and cancellation for passengers */
@RestController
@RequestMapping("customer")
public class Booking_Controller {

	@org.springframework.beans.factory.annotation.Autowired
	private com.project.railway.service.RunService runService;


	@org.springframework.beans.factory.annotation.Autowired
	private com.project.railway.service.ChartService chartService;


	@Autowired
	private BookingService bookingService;
	@Autowired
	private com.project.railway.service.PaymentService paymentService;
	@Autowired
	private com.project.railway.service.InfoService infoService;

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
			@RequestParam(defaultValue = "GN") String quota, @RequestParam(required = false) String payment_id,
			@RequestParam(defaultValue = "false") boolean vikalp) {
		return bookingService.book(token, train_no, seat_type, book_date, from, to, quota, passengers, payment_id, vikalp);
	}

	/* ------------------------------------------------------------ test payments */

	/** Starts a test payment. Body: {"amount", "method": "CARD|UPI|NETBANKING", card fields / "upiId" / "bank"} */
	@PostMapping("/payments")
	public ResponseEntity<ResponseStructure<com.project.railway.service.PaymentService.PaymentView>> startPayment(@RequestHeader("Bearer") String token,
			@RequestBody com.project.railway.service.PaymentService.StartRequest body) {
		return paymentService.start(token, body);
	}

	/** Completes it: {"otp": "123456"} for cards, {"approve": true|false} for UPI and net banking */
	@PostMapping("/payments/{id}/confirm")
	public ResponseEntity<ResponseStructure<com.project.railway.service.PaymentService.PaymentView>> confirmPayment(
			@RequestHeader("Bearer") String token, @PathVariable String id,
			@RequestBody(required = false) com.project.railway.service.PaymentService.ConfirmRequest body) {
		return paymentService.confirm(token, id, body);
	}

	@GetMapping("/payments")
	public ResponseEntity<ResponseStructure<List<com.project.railway.service.PaymentService.PaymentView>>> myPayments(
			@RequestHeader("Bearer") String token) {
		return paymentService.mine(token);
	}

	@GetMapping("/payments/banks")
	public ResponseEntity<ResponseStructure<List<String>>> banks() {
		ResponseStructure<List<String>> body = new ResponseStructure<>();
		body.setStatus(200);
		body.setMessage("Banks");
		body.setData2(com.project.railway.service.PaymentService.BANKS);
		return ResponseEntity.ok(body);
	}

	/* ------------------------------------------------------------ timetable and e-tickets */

	/** A train's timetable: stops, times, days, km. Public. */
	@GetMapping("/timetable/{train_no}")
	public ResponseEntity<ResponseStructure<TravelViews.Timetable>> timetable(@PathVariable int train_no) {
		return infoService.timetable(train_no);
	}

	/** Trains by number or name (at least 2 characters). Public. */
	@GetMapping("/train-search")
	public ResponseEntity<ResponseStructure<List<TravelViews.TrainBrief>>> findTrains(@RequestParam(defaultValue = "") String q) {
		return infoService.findTrains(q);
	}

	/** Signed code for the QR on an e-ticket */
	@GetMapping("/ticket-code/{pnr}")
	public ResponseEntity<ResponseStructure<String>> ticketCode(@RequestHeader("Bearer") String token,
			@PathVariable long pnr) {
		return infoService.ticketCode(token, pnr);
	}

	/** Public: checks an e-ticket's QR code */
	@GetMapping("/verify-ticket")
	public ResponseEntity<ResponseStructure<TravelViews.TicketCheck>> verifyTicket(@RequestParam String code) {
		return infoService.verifyTicket(code);
	}

	/**
	 * Chart & vacancy: every coach's berths and the stretches each is booked for, once the chart
	 * is prepared. Public, and never includes names, ages or PNRs. date is yyyy-MM-dd.
	 */
	@GetMapping("/chart/{train_no}")
	public ResponseEntity<ResponseStructure<ChartView>> chart(@PathVariable int train_no, @RequestParam String date) {
		return chartService.publicChart(train_no, date);
	}

	/** PNR status without logging in: no names, just each passenger's booking and current status */
	@GetMapping("/pnr-status/{pnr}")
	public ResponseEntity<ResponseStructure<TravelViews.PnrView>> publicPnr(@PathVariable long pnr) {
		return bookingService.publicPnr(pnr);
	}

	/** Running status of a train on a date (yyyy-MM-dd): cancelled, delay, where it is. Public. */
	@GetMapping("/running/{train_no}")
	public ResponseEntity<ResponseStructure<TravelViews.Running>> running(@PathVariable int train_no,
			@RequestParam String date) {
		return runService.running(train_no, date);
	}

	/** Two trains with one change, when no train runs all the way */
	@GetMapping("/connections")
	public ResponseEntity<ResponseStructure<List<TravelViews.Connection>>> connections(@RequestHeader("Bearer") String token,
			@RequestParam String from, @RequestParam String to, @RequestParam String date) {
		return bookingService.connections(token, from, to, date);
	}

	/** The passenger's saved travellers (master list) */
	@GetMapping("/saved_passengers")
	public ResponseEntity<ResponseStructure<List<SavedPassenger>>> savedPassengers(@RequestHeader("Bearer") String token) {
		return bookingService.savedPassengers(token);
	}

	/** Saves (or updates, by name) a traveller. Body: {"name","age","gender","berthPreference","meal"} */
	@PostMapping("/saved_passengers")
	public ResponseEntity<ResponseStructure<List<SavedPassenger>>> savePassenger(@RequestHeader("Bearer") String token,
			@RequestBody PassengerRequest passenger) {
		return bookingService.savePassenger(token, passenger);
	}

	@DeleteMapping("/saved_passengers/{id}")
	public ResponseEntity<ResponseStructure<List<SavedPassenger>>> deleteSavedPassenger(
			@RequestHeader("Bearer") String token, @PathVariable long id) {
		return bookingService.deleteSavedPassenger(token, id);
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

	/** What cancelling the listed passengers (all when empty) would refund; changes nothing */
	@PostMapping("/cancel_preview/{pnr}")
	public ResponseEntity<ResponseStructure<List<com.project.railway.service.BookingEngine.RefundPreview>>> cancelPreview(
			@RequestHeader("Bearer") String token, @PathVariable long pnr,
			@RequestBody(required = false) List<Long> passengerIds) {
		return bookingService.cancelPreview(token, pnr, passengerIds);
	}

	/** Cancels the listed passenger ids, or the whole booking when the body is empty */
	@PostMapping("/cancel/{pnr}")
	public ResponseEntity<ResponseStructure<Booking>> cancel(@RequestHeader("Bearer") String token,
			@PathVariable long pnr, @RequestBody(required = false) List<Long> passengerIds) {
		return bookingService.cancel(token, pnr, passengerIds);
	}
}
