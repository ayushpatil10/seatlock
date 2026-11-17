package com.seatlock.backend.controller;

import com.seatlock.backend.model.Seat;
import com.seatlock.backend.service.SeatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping("/api/v1/events/{eventId}/seats")
    public ResponseEntity<List<Seat>> getSeats(@PathVariable Integer eventId) {
        if (eventId == null || eventId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId must be a positive integer");
        }
        return ResponseEntity.ok(seatService.getSeatsByEventId(eventId));
    }

    @PostMapping("/api/v1/events/{eventId}/seats")
    public ResponseEntity<Seat> createSeat(
            @PathVariable Integer eventId,
            @RequestBody Seat seat) {

        if (eventId == null || eventId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId must be a positive integer");
        }
        if (seat == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body must not be null");
        }
        if (seat.getSeatNumber() == null || seat.getSeatNumber().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seatNumber must not be blank");
        }

        seat.setEventId(eventId);
        return ResponseEntity.ok(seatService.createSeat(seat));
    }

    @PostMapping("/api/v1/events/{eventId}/seats/generate")
    public ResponseEntity<List<Seat>> generateSeats(
            @PathVariable Integer eventId,
            @RequestParam int rows,
            @RequestParam int seatsPerRow) {

        if (eventId == null || eventId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId must be a positive integer");
        }
        if (rows <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rows must be a positive integer");
        }
        if (seatsPerRow <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seatsPerRow must be a positive integer");
        }

        return ResponseEntity.ok(seatService.generateSeats(eventId, rows, seatsPerRow));
    }

    @PutMapping("/api/v1/seats/{seatId}/lock")
    public ResponseEntity<Seat> lockSeat(@PathVariable Integer seatId) {
        if (seatId == null || seatId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seatId must be a positive integer");
        }
        return ResponseEntity.ok(seatService.lockSeat(seatId));
    }
}
