package com.project.railway.dto;

import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Data
@Entity
@Component
public class Route {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long routeId;
	private Long trainId;
	@ManyToMany
	private List<Station> stations;
	@ManyToOne
	private Train train; // Many-to-One relationship with TrainDTO

}
