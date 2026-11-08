package com.project.railway.dto;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import lombok.Data;

@Entity
@Data
@Component
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "trainNumber")
public class Train {

	@Id
	private int trainNumber;
	private String trainName;
	private String sourceStation;
	private String destinationStation;
	private String train_type;

	@OneToOne(cascade = { CascadeType.ALL, CascadeType.MERGE }, fetch = FetchType.EAGER, orphanRemoval = true)
	private Route route;

	@OneToOne(cascade = { CascadeType.ALL, CascadeType.MERGE }, fetch = FetchType.EAGER, orphanRemoval = true)
	private Schedule schedule;

	/** Stops in route order (they are saved in the order the admin enters them) */
	@OneToMany(cascade = { CascadeType.ALL }, fetch = FetchType.EAGER, orphanRemoval = true)
	@OrderBy("stationId ASC")
	private List<Station> stations;

	@OneToMany(mappedBy = "train", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<Coach> coaches;
}