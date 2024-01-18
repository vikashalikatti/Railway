package com.project.railway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.project.railway")
public class RailwayApplication {

	public static void main(String[] args) {
		SpringApplication.run(RailwayApplication.class, args);
	}

}
