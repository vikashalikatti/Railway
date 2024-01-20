package com.project.railway.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "twilio")
@Data
@Component
public class Twilio_Configuration {

	private String account_sid;
	private String authToken;
	private String PhoneNumber;
}
