package com.seatlock.backend.service;

import com.seatlock.backend.model.Seat;
import com.seatlock.backend.repository.SeatRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeatService {

    private final SeatRepository seatRepository;

    public SeatService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    public List<Seat> getSeatsByEventId(Integer eventId) {
        return seatRepository.findByEventId(eventId);
    }

    public Seat getSeatById(Integer seatId) {
        return seatRepository.findById(seatId)
                .orElseThrow(() -> new RuntimeException("Seat not found"));
    }

    public Seat createSeat(Seat seat) {
        return seatRepository.save(seat);
    }

    public List<Seat> generateSeats(Integer eventId, int rows, int seatsPerRow) {

        List<Seat> seats = new java.util.ArrayList<>();

        for (int row = 0; row < rows; row++) {
            char rowName = (char) ('A' + row);

            for (int number = 1; number <= seatsPerRow; number++) {
                Seat seat = new Seat();
                seat.setEventId(eventId);
                seat.setSeatNumber(rowName + String.valueOf(number));
                seat.setStatus("AVAILABLE");

                seats.add(seatRepository.save(seat));
            }
        }

        return seats;
    }
}
