package com.project.railway.dto;

import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import lombok.Data;

@Entity
@Data
@Component
public class Train {

	@Id
	private int trainNumber;
	private String trainName;
	private String sourceStation;
	private String destinationStation;
	@ManyToMany
	private List<Seat> seats;
	@ManyToMany
	private List<Route> routes;
	@OneToOne
	private Schedule schedule;

}
