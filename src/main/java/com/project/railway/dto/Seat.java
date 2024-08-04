package com.project.railway.dto;

import java.util.List;

import org.springframework.stereotype.Component;

import com.project.railway.helper.Seat_type;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Data;

@Entity
@Data
@Component
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seatId;
    private int sleeper_class;
    private int ac3_tier;
    private int ac2_tier;
    private int ac1_tier;
    private int second_class;
    private boolean second_class_isAvailable;
    private int total_seat;

    @OneToOne(mappedBy = "seat")
    private Train train;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Booking> bookings;

    public boolean isAvailable(Seat_type seatType) {
        switch (seatType) {
            case SLEEPER_CLASS:
                return sleeper_class > 0;
            case AC1_TIER:
                return ac3_tier > 0;
            case AC2_TIER:
                return ac2_tier > 0;
            case AC3_TIER:
                return ac1_tier > 0;
            case SECOND_CLASS:
                return second_class > 0 && second_class_isAvailable;
            default:
                return false;
        }
    }

    public void reserve(Seat_type seatType) {
        switch (seatType) {
            case SLEEPER_CLASS:
                if (sleeper_class > 0) {
                    sleeper_class--;
                }
                break;
            case AC1_TIER:
                if (ac3_tier > 0) {
                    ac3_tier--;
                }
                break;
            case AC2_TIER:
                if (ac2_tier > 0) {
                    ac2_tier--;
                }
                break;
            case AC3_TIER:
                if (ac1_tier > 0) {
                    ac1_tier--;
                }
                break;
            case SECOND_CLASS:
                if (second_class > 0 && second_class_isAvailable) {
                    second_class--;
                }
                break;
        }
        total_seat--;
    }
}
