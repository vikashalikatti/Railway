package com.project.railway.helper;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import com.project.railway.dto.Customer;

import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends one-time passwords by email (replaces the old Twilio SMS service).
 *
 * The OTP is stored on the Customer (otp + setOtpGeneratedTime), exactly as before,
 * so verify_otp and forgot-otp keep working unchanged.
 */
@Service
public class EmailOtpService {

	/** How long an OTP stays valid, in minutes */
	public static final int OTP_VALID_MINUTES = 5;

	public enum Purpose {
		SIGNUP("Verify your email", "Use this code to finish creating your Railway account."),
		RESET_PASSWORD("Reset your password", "Use this code to reset your Railway password.");

		final String subject;
		final String intro;

		Purpose(String subject, String intro) {
			this.subject = subject;
			this.intro = intro;
		}
	}

	private static final SecureRandom RANDOM = new SecureRandom();

	@Autowired
	private JavaMailSender javaMailSender;

	@Autowired
	private Configuration freemarker;

	/** The Gmail address in spring.mail.username is used as the sender */
	@Value("${spring.mail.username}")
	private String fromAddress;

	/**
	 * Generates a new 6-digit OTP, stores it on the customer and emails it.
	 * The caller must save the customer afterwards.
	 *
	 * @return true if the email was sent
	 */
	public boolean sendOtp(Customer customer, Purpose purpose) {
		if (customer == null || customer.getEmail() == null) {
			return false;
		}

		int otp = 100000 + RANDOM.nextInt(900000);
		customer.setOtp(otp);
		customer.setSetOtpGeneratedTime(LocalDateTime.now());

		try {
			Map<String, Object> model = new HashMap<>();
			model.put("name", customer.getName() == null ? "there" : customer.getName());
			model.put("otp", String.valueOf(otp));
			model.put("intro", purpose.intro);
			model.put("minutes", OTP_VALID_MINUTES);

			Template template = freemarker.getTemplate("otp.ftl");
			String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);

			MimeMessage message = javaMailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(fromAddress, "Railway");
			helper.setTo(customer.getEmail());
			helper.setSubject(purpose.subject + " – your OTP is " + otp);
			// plain-text version for mail apps that don't show HTML, then the HTML version
			helper.setText(
					"Hello " + model.get("name") + ",\n\n" + purpose.intro + "\n\nYour OTP: " + otp
							+ "\n\nIt expires in " + OTP_VALID_MINUTES
							+ " minutes. If you didn't ask for this, you can ignore this email.\n\nRailway",
					html);

			javaMailSender.send(message);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/** True when the customer's stored OTP was generated less than OTP_VALID_MINUTES ago */
	public boolean isOtpValid(Customer customer) {
		if (customer == null || customer.getSetOtpGeneratedTime() == null) {
			return false;
		}
		long minutes = ChronoUnit.MINUTES.between(customer.getSetOtpGeneratedTime(), LocalDateTime.now());
		return minutes < OTP_VALID_MINUTES;
	}
}
