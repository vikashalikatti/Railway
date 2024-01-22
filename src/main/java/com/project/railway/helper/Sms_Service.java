package com.project.railway.helper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.project.railway.configuration.Twilio_Configuration;
import com.project.railway.dto.Customer;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

@Service
public class Sms_Service {
	@Autowired
	private Twilio_Configuration configuration;

	public boolean smsSent(Customer customer) throws Exception {
		if (customer != null) {
			int otp = new Random().nextInt(100000, 999999);
			customer.setOtp(otp);
			customer.setSetOtpGeneratedTime(LocalDateTime.now());
			String mobile = "+" + String.valueOf(customer.getMobile());
			String sms = "Your Otp##"+otp+",Please use this otp to verify the account";
			Twilio.init(configuration.getAccountSid(), configuration.getAuthToken());

			Message message = Message.creator(new PhoneNumber(mobile), new PhoneNumber(configuration.getTrailNumber()),
					sms).create();
		System.out.println(message.getStatus());
			return true;
		} else {
			return false;
		}
	}

	public boolean isOtpValid(Customer customer) {
		if (customer != null && customer.getSetOtpGeneratedTime() != null) {
			LocalDateTime currentTime = LocalDateTime.now();
			LocalDateTime otpGeneratedTime = customer.getSetOtpGeneratedTime();
			long minutesElapsed = ChronoUnit.MINUTES.between(otpGeneratedTime, currentTime);
			return minutesElapsed <= 5;
		} else {
			return false;
		}
	}

}
