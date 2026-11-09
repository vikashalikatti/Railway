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
import com.project.railway.dto.Station;
import com.project.railway.dto.Train;
import com.project.railway.helper.CoachLayout;
import com.project.railway.helper.Quota;
import com.project.railway.helper.Seat_type;
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
		BOOKED, CONFIRMED_FROM_WAITLIST, CANCELLED, CHART_PREPARED
	}

	/** One row of the passenger table */
	public record Line(int no, String name, int age, String gender, String bookingStatus, String currentStatus,
			boolean confirmed, boolean cancelled) {
	}

	/** Everything the email shows; plain values, safe to use after the transaction has ended */
	public record Ticket(Event event, String email, String customerName, long pnr, int trainNumber, String trainName,
			String fromStation, String toStation, String journeyDate, String departs, String arrives,
			String classLabel, String quotaLabel, String status, String totalFare, String distance, String bookedAt,
			List<Line> passengers) {
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
		try {
			return buildTicket(b, event);
		} catch (RuntimeException e) {
			log.error("Ticket email for PNR {} could not be prepared: {}", b.getBookingId(), e.toString());
			return null;
		}
	}

	private Ticket buildTicket(Booking b, Event event) {
		Customer c = b.getCustomer();
		Seat_type type = Seat_type.valueOf(b.getSeatType());
		Quota quota = Quota.parse(b.getQuota());

		String departs = "";
		String arrives = "";
		Train train = trainRepository.findByTrainNumber(b.getTrainNumber());
		if (train != null && train.getStations() != null && b.getToStop() < train.getStations().size()) {
			departs = time(train.getStations().get(b.getFromStop()).getDepartureTime());
			Station end = train.getStations().get(b.getToStop());
			arrives = time(end.getArrivalTime());
		}

		List<Line> lines = new ArrayList<>();
		int i = 1;
		for (Passenger p : b.getPassengers()) {
			String booked = p.getWlNumber() != null ? waitLabel(quota, p.getWlNumber()) : seatLabel(p);
			String now;
			if (Passenger.CONFIRMED.equals(p.getStatus())) {
				now = seatLabel(p);
			} else if (Passenger.WAITLISTED.equals(p.getStatus())) {
				now = waitLabel(quota, p.getWlNumber() == null ? 0 : p.getWlNumber());
			} else if (Passenger.CANCELLED_AT_CHART.equals(p.getCancelReason())) {
				now = "Not confirmed at charting";
			} else {
				now = "Cancelled";
			}
			lines.add(new Line(i++, p.getName(), p.getAge(), p.getGender(), booked, now,
					Passenger.CONFIRMED.equals(p.getStatus()), Passenger.CANCELLED.equals(p.getStatus())));
		}

		return new Ticket(event, c.getEmail(), c.getName(), b.getBookingId(), b.getTrainNumber(), b.getTrainName(),
				b.getFromStation(), b.getToStation(), b.getJourneyDate().format(DAY), departs, arrives,
				label(type) + " (" + CoachLayout.shortCode(type) + ")", quota.label, statusLabel(b.getStatus()),
				String.format(Locale.ENGLISH, "₹%,.0f", b.getTotalFare()),
				String.format(Locale.ENGLISH, "%.0f km", b.getDistanceKm()),
				b.getBookedAt() == null ? "" : b.getBookedAt().format(BOOKED_AT), lines);
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
		case CANCELLED:
			return "Cancellation – PNR " + t.pnr() + " – " + trip;
		case CHART_PREPARED:
			return "Chart prepared – final status of PNR " + t.pnr() + " – " + trip;
		default:
			return "Ticket booked – PNR " + t.pnr() + " – " + trip;
		}
	}

	static String title(Event e) {
		switch (e) {
		case CONFIRMED_FROM_WAITLIST:
			return "Your ticket is confirmed";
		case CANCELLED:
			return "Cancellation done";
		case CHART_PREPARED:
			return "Chart prepared";
		default:
			return "Booking successful";
		}
	}

	static String intro(Event e) {
		switch (e) {
		case CONFIRMED_FROM_WAITLIST:
			return "Seats were freed by a cancellation, and your waitlisted passengers now have a coach and berth.";
		case CANCELLED:
			return "The passengers marked Cancelled below no longer travel on this ticket.";
		case CHART_PREPARED:
			return "The reservation chart for your train has been prepared. This is your final status. "
					+ "Passengers still on the waitlist were not confirmed and don't travel. "
					+ "To see which berths are still vacant, open Chart & vacancy in the Railway app.";
		default:
			return "Your ticket is booked. Keep this email: you'll need the PNR to check status or cancel.";
		}
	}

	private static String plainText(Ticket t) {
		StringBuilder sb = new StringBuilder();
		sb.append(title(t.event())).append("\n\n").append(intro(t.event())).append("\n\n");
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

	static String seatLabel(Passenger p) {
		return "CNF " + p.getCoachNumber() + "/" + p.getSeatNumber() + "/" + berth(p.getBerthType());
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
