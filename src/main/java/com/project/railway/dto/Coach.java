package com.project.railway.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
    /** No longer used: fares are worked out per journey by FareCalculator */
    private Double total_price;

    @ManyToOne
    @JoinColumn(name = "train_number")
    private Train train;

    /** First journey date this coach runs on; empty = from the start */
    @Column(name = "valid_from")
    private java.time.LocalDate validFrom;

    /** Last journey date this coach runs on; empty = no end (extra coaches for rush days have one) */
    @Column(name = "valid_to")
    private java.time.LocalDate validTo;

    /** Does this coach run on this journey date? */
    public boolean runsOn(java.time.LocalDate date) {
        return (validFrom == null || !date.isBefore(validFrom)) && (validTo == null || !date.isAfter(validTo));
    }
}

