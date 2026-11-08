package com.project.railway.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
	/** Can be sent in (signup) but is never sent back */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String password;
	private String address;
	@JsonIgnore
	@Column(columnDefinition = "MEDIUMBLOB")
	private byte[] photo;
	private String gender;
	private long mobile;
	@JsonIgnore
	private long otp;
	@JsonIgnore
	private LocalDateTime setOtpGeneratedTime;
	/** Wrong OTP entries since the last OTP was sent; the OTP is cancelled after 5 */
	@JsonIgnore
	@Column(name = "otp_attempts", columnDefinition = "int default 0")
	private Integer otpAttempts;
	/** Raised on "log out everywhere" and password reset; tokens carrying an older number stop working */
	@JsonIgnore
	@Column(name = "token_version", columnDefinition = "int default 0")
	private Integer tokenVersion;
	private boolean status;
	private String role;
	private int age;

	@JsonIgnore
	@OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Booking> bookings;
}
