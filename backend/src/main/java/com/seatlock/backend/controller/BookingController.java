package com.seatlock.backend.controller;

import com.seatlock.backend.model.Booking;
import com.seatlock.backend.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<Booking> createBooking(@RequestBody Booking booking) {
        // Validate seatId: must be non-null and positive
        if (booking.getSeatId() == null || booking.getSeatId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seatId must be a positive integer");
        }

        // Validate eventId: must be non-null and positive
        if (booking.getEventId() == null || booking.getEventId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId must be a positive integer");
        }

        // Validate idempotencyKey: must be non-null and non-blank
        if (booking.getIdempotencyKey() == null || booking.getIdempotencyKey().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idempotencyKey must not be blank");
        }

        Booking savedBooking = bookingService.createBooking(booking);
        return ResponseEntity.ok(savedBooking);
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<Booking> getBooking(@PathVariable Integer bookingId) {
        Booking booking = bookingService.getBookingById(bookingId);
        return ResponseEntity.ok(booking);
    }

    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Booking> cancelBooking(@PathVariable Integer bookingId) {
        Booking cancelledBooking = bookingService.cancelBooking(bookingId);
        return ResponseEntity.ok(cancelledBooking);
    }

    @PostMapping("/{bookingId}/confirm")
    public ResponseEntity<Booking> confirmBooking(@PathVariable Integer bookingId) {
        Booking confirmedBooking = bookingService.confirmBooking(bookingId);
        return ResponseEntity.ok(confirmedBooking);
    }

    @PostMapping("/{bookingId}/expire")
    public ResponseEntity<Booking> expireBooking(@PathVariable Integer bookingId) {
        Booking expiredBooking = bookingService.expireBooking(bookingId);
        return ResponseEntity.ok(expiredBooking);
    }
}
