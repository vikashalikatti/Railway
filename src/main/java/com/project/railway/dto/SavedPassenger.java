package com.project.railway.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A passenger saved by a customer (family, friends) to add to bookings in one click */
@Entity
@Table(name = "saved_passenger")
@Getter
@Setter
public class SavedPassenger {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	@Column(nullable = false, length = 50)
	private String name;
	private int age;
	@Column(length = 10)
	private String gender;
	@Column(name = "berth_preference", length = 20)
	private String berthPreference;
	@Column(length = 10)
	private String meal;
}
