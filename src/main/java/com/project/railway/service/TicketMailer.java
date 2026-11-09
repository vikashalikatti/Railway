package com.project.railway.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import com.project.railway.dto.Booking;
import com.project.railway.dto.Customer;
import com.project.railway.dto.Passenger;
import com.project.railway.dto.Train;
import com.project.railway.helper.CoachLayout;
import com.project.railway.helper.Quota;
import com.project.railway.helper.Seat_type;
import com.project.railway.helper.TravelRules;
import com.project.railway.repository.Train_Repository;

import freemarker.template.Configuration;
import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.MimeMessage;

/**
 * Ticket emails, like a railway e-ticket: booked, waitlist confirmed, cancelled, chart prepared.
 *
 * The email's content is gathered inside the booking transaction (ticket()), and it is only
 * sent once that transaction has committed (sendAfterCommit()), in the background. So a booking
 * that rolls back never sends an email, and a slow or failing mail server never slows down or
 * breaks a booking.
 */
@Service
public class TicketMailer {

	private static final Logger log = LoggerFactory.getLogger(TicketMailer.class);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter BOOKED_AT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

	public enum Event {
		BOOKED, CONFIRMED_FROM_WAITLIST, RAC_FROM_WAITLIST, CANCELLED, CHART_PREPARED, TRAIN_CANCELLED, RESCHEDULED,
		VIKALP_MOVED, RUNNING_LATE
	}

	/** One row of the passenger table */
	public record Line(int no, String name, int age, String gender, String bookingStatus, String currentStatus,
			boolean confirmed, boolean cancelled) {
	}

	/** Everything the email shows; plain values, safe to use after the transaction has ended */
	public record Ticket(Event event, String email, String customerName, long pnr, int trainNumber, String trainName,
			String fromStation, String toStation, String journeyDate, String departs, String arrives,
			String classLabel, String quotaLabel, String status, String totalFare, String distance, String bookedAt,
			List<Line> passengers, String note, String refund) {
	}

	@Autowired
	private JavaMailSender mailSender;
	@Autowired
	private Configuration freemarker;
	@Autowired
	private Train_Repository trainRepository;

	@Value("${spring.mail.username}")
	private String fromAddress;

	/** Two background threads are plenty for email; they never hold up a request */
	private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "ticket-mail");
		t.setDaemon(true);
		return t;
	});

	/**
	 * Reads what the email needs. Call inside the transaction (it reads the customer and the train).
	 * Never throws: a problem here must not cancel the booking itself, so it returns null and logs.
	 */
	public Ticket ticket(Booking b, Event event) {
		return ticket(b, event, null);
	}

	/** note: extra line for this email, e.g. the reason a train was cancelled or the new departure time */
	public Ticket ticket(Booking b, Event event, String note) {
		try {
			return buildTicket(b, event, note);
		} catch (RuntimeException e) {
			log.error("Ticket email for PNR {} could not be prepared: {}", b.getBookingId(), e.toString());
			return null;
		}
	}

	private Ticket buildTicket(Booking b, Event event, String note) {
		Customer c = b.getCustomer();
		Seat_type type = Seat_type.valueOf(b.getSeatType());
		Quota quota = Quota.parse(b.getQuota());

		String departs = "";
		String arrives = "";
		java.time.LocalDate boarding = b.getBoardingDate() != null ? b.getBoardingDate() : b.getJourneyDate();
		Train train = trainRepository.findByTrainNumber(b.getTrainNumber());
		if (train != null && train.getStations() != null && b.getToStop() < train.getStations().size()) {
			var tl = Timelines.of(train);
			var leaves = tl.departs(b.getJourneyDate(), b.getFromStop());
			var reaches = tl.arrives(b.getJourneyDate(), b.getToStop());
			departs = leaves.format(TIME);
			long days = java.time.temporal.ChronoUnit.DAYS.between(leaves.toLocalDate(), reaches.toLocalDate());
			arrives = reaches.format(TIME) + (days > 0 ? " (" + reaches.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + ")" : "");
			boarding = leaves.toLocalDate();
		}

		List<Line> lines = new ArrayList<>();
		int i = 1;
		for (Passenger p : b.getPassengers()) {
			boolean noBerth = Boolean.TRUE.equals(p.getNoBerth());
			String booked = bookedLabel(p, quota);
			String now = currentLabel(p, quota);
			String extras = extrasLabel(p);
			if (Passenger.CONFIRMED.equals(p.getStatus()) && p.getCompanionNote() != null) {
				extras = extras.isEmpty() ? p.getCompanionNote() : extras + ". " + p.getCompanionNote();
			} else if (Passenger.CONFIRMED.equals(p.getStatus()) && Boolean.TRUE.equals(p.getNeedsAssistance())) {
				extras = (extras.isEmpty() ? "" : extras + ". ") + "Boarding help asked for; no helper on this coach yet";
			}
			lines.add(new Line(i++, p.getName(), p.getAge(), p.getGender() + (extras.isEmpty() ? "" : ", " + extras), booked,
					now, Passenger.CONFIRMED.equals(p.getStatus()) || Passenger.NO_BERTH.equals(p.getStatus())
							|| Passenger.RAC.equals(p.getStatus()),
					Passenger.CANCELLED.equals(p.getStatus())));
		}

		if (note == null && b.getNote() != null && (event == Event.VIKALP_MOVED || event == Event.BOOKED)) {
			note = b.getNote();
		}
		return new Ticket(event, c.getEmail(), c.getName(), b.getBookingId(), b.getTrainNumber(), b.getTrainName(),
				b.getFromStation(), b.getToStation(), boarding.format(DAY), departs, arrives,
				label(type) + " (" + CoachLayout.shortCode(type) + ")", quota.label, statusLabel(b.getStatus()),
				String.format(Locale.ENGLISH, "₹%,.0f", b.getTotalFare()),
				String.format(Locale.ENGLISH, "%.0f km", b.getDistanceKm()),
				b.getBookedAt() == null ? "" : b.getBookedAt().format(BOOKED_AT), lines, note,
				b.getRefundAmount() == null || b.getRefundAmount() <= 0 ? null
						: String.format(Locale.ENGLISH, "₹%,.0f", b.getRefundAmount()));
	}

	/** Sends once the current transaction commits; immediately if there is no transaction */
	public void sendAfterCommit(Ticket ticket) {
		if (ticket == null) {
			return;
		}
		log.info("Ticket email queued: {} for PNR {} to {}", ticket.event(), ticket.pnr(), ticket.email());
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					send(ticket);
				}
			});
		} else {
			send(ticket);
		}
	}

	private void send(Ticket t) {
		if (t.email() == null || t.email().isBlank()) {
			log.warn("Ticket email for PNR {} not sent: the passenger's account has no email address", t.pnr());
			return;
		}
		pool.submit(() -> {
			String subject = subject(t);
			// The designed email; if the template ever fails, the plain-text version still goes out
			String html = null;
			try {
				Map<String, Object> model = new HashMap<>();
				model.put("t", t);
				model.put("title", title(t.event()));
				model.put("intro", intro(t.event()));
				html = FreeMarkerTemplateUtils.processTemplateIntoString(freemarker.getTemplate("ticket.ftl"), model);
			} catch (Exception e) {
				log.warn("Ticket email template failed for PNR {}, sending plain text instead: {}", t.pnr(),
						e.toString());
			}
			try {
				MimeMessage message = mailSender.createMimeMessage();
				MimeMessageHelper helper = new MimeMessageHelper(message, html != null, "UTF-8");
				helper.setFrom(fromAddress, "Railway");
				helper.setTo(t.email());
				helper.setSubject(subject);
				if (html != null) {
					helper.setText(plainText(t), html);
				} else {
					helper.setText(plainText(t), false);
				}
				mailSender.send(message);
				log.info("Ticket email sent: {} to {}", subject, t.email());
			} catch (Exception e) {
				log.error("Ticket email for PNR {} to {} was NOT sent (check spring.mail.username and MAIL_PASSWORD): {}",
						t.pnr(), t.email(), e.toString());
			}
		});
	}

	@PreDestroy
	void stop() {
		pool.shutdown();
	}

	/* ------------------------------------------------------------ wording */

	static String subject(Ticket t) {
		String trip = t.trainNumber() + " " + t.trainName() + ", " + t.journeyDate();
		switch (t.event()) {
		case CONFIRMED_FROM_WAITLIST:
			return "Your waitlisted ticket is confirmed – PNR " + t.pnr() + " – " + trip;
		case RAC_FROM_WAITLIST:
			return "Your waitlisted ticket moved to RAC – PNR " + t.pnr() + " – " + trip;
		case VIKALP_MOVED:
			return "Vikalp: you have a confirmed seat on another train – PNR " + t.pnr() + " – " + trip;
		case RUNNING_LATE:
			return "Train running late – PNR " + t.pnr() + " – " + trip;
		case CANCELLED:
			return "Cancellation – PNR " + t.pnr() + " – " + trip;
		case CHART_PREPARED:
			return "Chart prepared – final status of PNR " + t.pnr() + " – " + trip;
		case TRAIN_CANCELLED:
			return "Train cancelled – PNR " + t.pnr() + " – full refund – " + trip;
		case RESCHEDULED:
			return "Train rescheduled – PNR " + t.pnr() + " – " + trip;
		default:
			return "Ticket booked – PNR " + t.pnr() + " – " + trip;
		}
	}

	static String title(Event e) {
		switch (e) {
		case CONFIRMED_FROM_WAITLIST:
			return "Your ticket is confirmed";
		case RAC_FROM_WAITLIST:
			return "You're on RAC now";
		case VIKALP_MOVED:
			return "Moved to another train (Vikalp)";
		case RUNNING_LATE:
			return "Your train is running late";
		case CANCELLED:
			return "Cancellation done";
		case CHART_PREPARED:
			return "Chart prepared";
		case TRAIN_CANCELLED:
			return "Your train has been cancelled";
		case RESCHEDULED:
			return "Your train has been rescheduled";
		default:
			return "Booking successful";
		}
	}

	static String intro(Event e) {
		switch (e) {
		case CONFIRMED_FROM_WAITLIST:
			return "Seats were freed by a cancellation, and your waitlisted passengers now have a coach and berth.";
		case RAC_FROM_WAITLIST:
			return "Your waitlisted passengers moved up to RAC: they can travel, sharing a side-lower berth until a berth frees up.";
		case VIKALP_MOVED:
			return "Your passengers were still waitlisted when the chart was prepared, so under Vikalp they were moved to "
					+ "the train below, where they have confirmed berths. There is no extra charge.";
		case RUNNING_LATE:
			return "Your train is running late. Please check the expected time below before you leave for the station.";
		case CANCELLED:
			return "The passengers marked Cancelled below no longer travel on this ticket.";
		case CHART_PREPARED:
			return "The reservation chart for your train has been prepared. This is your final status. "
					+ "Passengers still on the waitlist were not confirmed and don't travel. "
					+ "To see which berths are still vacant, open Chart & vacancy in the Railway app.";
		case TRAIN_CANCELLED:
			return "The railway has cancelled this train on your journey date. Your ticket is cancelled "
					+ "and the full fare is refunded; you don't need to do anything.";
		case RESCHEDULED:
			return "The railway has changed this train's departure. Please plan to reach the station for the new time below.";
		default:
			return "Your ticket is booked. Keep this email: you'll need the PNR to check status or cancel.";
		}
	}

	private static String plainText(Ticket t) {
		StringBuilder sb = new StringBuilder();
		sb.append(title(t.event())).append("\n\n").append(intro(t.event())).append("\n\n");
		if (t.note() != null) {
			sb.append(t.note()).append("\n\n");
		}
		if (t.refund() != null) {
			sb.append("Refund: ").append(t.refund()).append("\n\n");
		}
		sb.append("PNR: ").append(t.pnr()).append("\nTrain: ").append(t.trainNumber()).append(' ').append(t.trainName())
				.append("\nDate: ").append(t.journeyDate()).append("\nFrom: ").append(t.fromStation()).append(' ')
				.append(t.departs()).append("\nTo: ").append(t.toStation()).append(' ').append(t.arrives())
				.append("\nClass: ").append(t.classLabel()).append("\nQuota: ").append(t.quotaLabel())
				.append("\nFare: ").append(t.totalFare()).append("\n\nPassengers:\n");
		for (Line l : t.passengers()) {
			sb.append(l.no()).append(". ").append(l.name()).append(" (").append(l.age()).append(", ")
					.append(l.gender()).append(") – booked: ").append(l.bookingStatus()).append(", now: ")
					.append(l.currentStatus()).append('\n');
		}
		sb.append("\nCarry a photo ID when you travel.\n\nRailway");
		return sb.toString();
	}

	/** Status when booked: "CNF S1/33/Lower", "RAC 4", "WL 12" or the no-berth label */
	static String bookedLabel(Passenger p, Quota quota) {
		if (Boolean.TRUE.equals(p.getNoBerth())) {
			return noBerthLabel(p);
		}
		if (p.getWlNumber() != null) {
			return waitLabel(quota, p.getWlNumber());
		}
		if (p.getRacNumber() != null) {
			return "RAC " + p.getRacNumber();
		}
		return seatLabel(p);
	}

	/** Status now, including the refund for a cancelled passenger */
	static String currentLabel(Passenger p, Quota quota) {
		String refund = p.getRefund() != null && p.getRefund() > 0
				? String.format(Locale.ENGLISH, " (refund ₹%,.0f)", p.getRefund())
				: "";
		if (Passenger.NO_BERTH.equals(p.getStatus())) {
			return noBerthLabel(p);
		}
		if (Passenger.CANCELLED_TRAIN.equals(p.getCancelReason())) {
			return "Train cancelled" + refund;
		}
		if (Passenger.CONFIRMED.equals(p.getStatus())) {
			return seatLabel(p);
		}
		if (Passenger.RAC.equals(p.getStatus())) {
			return racLabel(p);
		}
		if (Passenger.WAITLISTED.equals(p.getStatus())) {
			return waitLabel(quota, p.getCurrentWlPosition() != null ? p.getCurrentWlPosition()
					: p.getWlNumber() == null ? 0 : p.getWlNumber());
		}
		if (Passenger.CANCELLED_VIKALP.equals(p.getCancelReason())) {
			return "Moved to another train (Vikalp)";
		}
		if (Passenger.CANCELLED_AT_CHART.equals(p.getCancelReason())) {
			return "Not confirmed at charting" + refund;
		}
		return "Cancelled" + refund;
	}

	/** "RAC 3", or after the chart "RAC S1/39 (shared side lower)" */
	static String racLabel(Passenger p) {
		if (p.getCoachNumber() != null && p.getSeatNumber() != null) {
			return "RAC " + p.getCoachNumber() + "/" + p.getSeatNumber() + " (shared side lower)";
		}
		Integer n = p.getCurrentRacPosition() != null ? p.getCurrentRacPosition() : p.getRacNumber();
		return "RAC " + (n == null ? "" : n);
	}

	static String seatLabel(Passenger p) {
		return "CNF " + p.getCoachNumber() + "/" + p.getSeatNumber() + "/" + berth(p.getBerthType());
	}

	static String noBerthLabel(Passenger p) {
		return p.getAge() < TravelRules.INFANT_UNDER ? "Infant, no berth (free)" : "Child, no berth";
	}

	/** "Veg meal, bedroll" or "" */
	static String extrasLabel(Passenger p) {
		List<String> parts = new ArrayList<>();
		TravelRules.Meal meal;
		try {
			meal = TravelRules.Meal.parse(p.getMeal());
		} catch (IllegalArgumentException e) {
			meal = TravelRules.Meal.NONE;
		}
		if (meal != TravelRules.Meal.NONE) {
			parts.add(meal.label + " meal");
		}
		if (Boolean.TRUE.equals(p.getBedroll())) {
			parts.add("bedroll");
		}
		return String.join(", ", parts);
	}

	static String waitLabel(Quota q, int number) {
		return (q.wlPrefix.isEmpty() ? "WL" : q.wlPrefix) + " " + number;
	}

	private static String berth(String b) {
		if (b == null) {
			return "";
		}
		switch (b) {
		case "SIDE_LOWER":
			return "SL";
		case "SIDE_MIDDLE":
			return "SM";
		case "SIDE_UPPER":
			return "SU";
		case "MIDDLE_SEAT":
			return "MIDDLE";
		default:
			return b;
		}
	}

	private static String statusLabel(String s) {
		switch (s) {
		case Booking.CONFIRMED:
			return "Confirmed";
		case Booking.WAITLISTED:
			return "Waitlisted";
		case Booking.PARTIAL:
			return "Partly confirmed";
		default:
			return "Cancelled";
		}
	}

	private static String label(Seat_type t) {
		switch (t) {
		case SECOND_CLASS:
			return "Second sitting";
		case SLEEPER_CLASS:
			return "Sleeper";
		case AC3_TIER:
			return "AC 3 tier";
		case AC2_TIER:
			return "AC 2 tier";
		case AC1_TIER:
			return "AC first class";
		case AC_CHAIR_CAR:
			return "AC chair car";
		case EXECUTIVE_CHAIR_CAR:
			return "Executive chair car";
		case AC3_ECONOMY:
			return "AC 3 economy";
		default:
			return t.name();
		}
	}

	private static String time(LocalTime t) {
		return t == null ? "" : t.format(TIME);
	}
}
