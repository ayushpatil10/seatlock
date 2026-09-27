package com.seatlock.backend.service;

import com.seatlock.backend.model.Seat;
import com.seatlock.backend.repository.SeatRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeatService {

    private final SeatRepository seatRepository;
    private final SeatLockService seatLockService;

    public SeatService(
            SeatRepository seatRepository,
            SeatLockService seatLockService) {

        this.seatRepository = seatRepository;
        this.seatLockService = seatLockService;
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

    public Seat lockSeat(Integer seatId) {

        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new RuntimeException("Seat not found"));

        // Release an expired lock
        if ("LOCKED".equals(seat.getStatus())
                && seat.getLockedUntil() != null
                && seat.getLockedUntil().isBefore(java.time.LocalDateTime.now())) {

            seat.setStatus("AVAILABLE");
            seat.setLockedUntil(null);
            seatRepository.save(seat);
        }

        // Check availability after releasing an expired lock
        if (!"AVAILABLE".equals(seat.getStatus())) {
            throw new RuntimeException("Seat is not available");
        }

        boolean locked = seatLockService.lockSeat(seatId);

        if (!locked) {
            throw new RuntimeException(
                    "Seat is currently being locked by another user"
            );
        }

        seat.setStatus("LOCKED");
        seat.setLockedUntil(
                java.time.LocalDateTime.now().plusMinutes(10)
        );

        return seatRepository.save(seat);
    }
}
