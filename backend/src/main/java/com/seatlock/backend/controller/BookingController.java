package com.seatlock.backend.controller;

import com.seatlock.backend.model.Booking;
import com.seatlock.backend.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<Booking> createBooking(@RequestBody Booking booking) {
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
