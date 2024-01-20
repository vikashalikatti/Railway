package com.project.railway.dto;

import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.Data;

@Data
@Entity
@Component
public class Station {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long stationId;
	private String stationName;
	@OneToMany
	private List<Route> routes; // One-to-Many relationship with RouteDTO

}
