package com.project.railway.dto;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
@Component
public class Customer {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;
	private String name;
	private String email;
	private String password;
	private String address;
	@Column(columnDefinition = "MEDIUMBLOB")
	private byte[] photo;
	private String gender;
	private long mobile;
	private long otp;
	private LocalDateTime setOtpGeneratedTime;
	private boolean status;
	private String role;
}
