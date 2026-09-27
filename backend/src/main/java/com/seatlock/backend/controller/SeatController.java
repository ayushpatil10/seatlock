package com.seatlock.backend.controller;

import com.seatlock.backend.model.Seat;
import com.seatlock.backend.service.SeatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping("/api/v1/events/{eventId}/seats")
    public List<Seat> getSeats(@PathVariable Integer eventId) {
        return seatService.getSeatsByEventId(eventId);
    }

    @PostMapping("/api/v1/events/{eventId}/seats")
    public Seat createSeat(
            @PathVariable Integer eventId,
            @RequestBody Seat seat) {

        seat.setEventId(eventId);
        return seatService.createSeat(seat);
    }

    @PostMapping("/api/v1/events/{eventId}/seats/generate")
    public List<Seat> generateSeats(
            @PathVariable Integer eventId,
            @RequestParam int rows,
            @RequestParam int seatsPerRow) {

        return seatService.generateSeats(eventId, rows, seatsPerRow);
    }

    @PutMapping("/api/v1/seats/{seatId}/lock")
    public Seat lockSeat(@PathVariable Integer seatId) {
        return seatService.lockSeat(seatId);
    }
}
