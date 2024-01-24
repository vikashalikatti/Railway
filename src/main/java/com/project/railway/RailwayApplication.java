package com.project.railway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import com.project.railway.configuration.Twilio_Configuration;
import com.twilio.Twilio;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
@ComponentScan(basePackages = "com.project.railway")
public class RailwayApplication {

	@Autowired
	private Twilio_Configuration configuration;

	@PostConstruct
	public void postConstruct() {
		Twilio.init(configuration.getAccountSid(), configuration.getAuthToken());
	}

	public static void main(String[] args) {
		SpringApplication.run(RailwayApplication.class, args);

	}
}
