package com.project.railway.dto;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class Coach {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;
	private String coachNumber;
	private String coachType;
	private int numberOfSeats;
	
//	@ManyToOne
//	Train train;


}
