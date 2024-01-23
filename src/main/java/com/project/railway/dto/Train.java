package com.project.railway.dto;

import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Seat> seats;

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Route> routes;

	@OneToOne
	private Schedule schedule;

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Station> stations;
}
