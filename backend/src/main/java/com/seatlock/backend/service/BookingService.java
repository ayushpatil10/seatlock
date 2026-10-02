package com.seatlock.backend.service;

import com.seatlock.backend.model.Booking;
import com.seatlock.backend.model.Seat;
import com.seatlock.backend.repository.BookingRepository;
import com.seatlock.backend.repository.SeatRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final SeatLockService seatLockService;

    public BookingService(
            BookingRepository bookingRepository,
            SeatRepository seatRepository,
            SeatLockService seatLockService) {
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.seatLockService = seatLockService;
    }

    @Transactional
    public Booking createBooking(Booking booking) {
        
        // 1. Idempotency Check Requirements
        if (booking.getIdempotencyKey() == null || booking.getIdempotencyKey().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency key is required");
        }

        // Fast path query (non-blocking)
        java.util.Optional<Booking> existingBooking = bookingRepository.findByIdempotencyKey(booking.getIdempotencyKey());
        if (existingBooking.isPresent()) {
            return existingBooking.get();
        }

        // Find the seat by seatId with Pessimistic Lock
        Seat seat = seatRepository.findByIdForUpdate(booking.getSeatId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found"));
        
        // 2. Double-check Idempotency after acquiring the DB lock.
        // If two equivalent requests hit this exact line sequentially, the latter safely escapes natively!
        existingBooking = bookingRepository.findByIdempotencyKey(booking.getIdempotencyKey());
        if (existingBooking.isPresent()) {
            return existingBooking.get();
        }

        // Verify the seat belongs to the requested event
        if (!seat.getEventId().equals(booking.getEventId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat does not belong to this event");
        }
        
        // Check whether the seat is already booked
        if (!"AVAILABLE".equals(seat.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat is already booked");
        }
        
        // Attempt the Redis lock
        boolean locked = seatLockService.lockSeat(booking.getSeatId());
        if (!locked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat is already locked");
        }
        
        // Update seat status
        seat.setStatus("BOOKED");
        seatRepository.save(seat);

        // Booking fields: id, seatId, eventId, status, createdAt, idempotencyKey
        booking.setStatus("PENDING");
        
        try {
            return bookingRepository.saveAndFlush(booking);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Transaction is marked for rollback! Do not attempt further DB queries.
            // Absolute catch-all safety net for completely different seatIds using identically malformed Idempotency constraints
            seatLockService.unlockSeat(booking.getSeatId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent idempotency conflict detected");
        }
    }

    public Booking getBookingById(Integer bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }
    
    @Transactional
    public Booking confirmBooking(Integer bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!"PENDING".equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only PENDING bookings can be confirmed");
        }

        if (booking.getCreatedAt().plusMinutes(10).isBefore(java.time.LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking hold has expired");
        }

        booking.setStatus("CONFIRMED");
        return bookingRepository.save(booking);
    }
    
    @Transactional
    public Booking expireBooking(Integer bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!"PENDING".equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only PENDING bookings can be expired");
        }

        if (booking.getCreatedAt().plusMinutes(10).isAfter(java.time.LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking hold period has not yet expired");
        }

        Seat seat = seatRepository.findByIdForUpdate(booking.getSeatId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found"));

        booking.setStatus("EXPIRED");
        seat.setStatus("AVAILABLE");
        seatLockService.unlockSeat(seat.getId());

        seatRepository.save(seat);
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking cancelBooking(Integer bookingId) {
        // Find the booking with Pessimistic Lock
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        // If booking status is already CANCELLED, return 409
        if ("CANCELLED".equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is already cancelled");
        }

        // Find the associated seat with Pessimistic Lock
        Seat seat = seatRepository.findByIdForUpdate(booking.getSeatId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found"));

        // Verify that the seat belongs to the booking's event
        if (!seat.getEventId().equals(booking.getEventId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat does not belong to this event");
        }

        // Change booking status to CANCELLED
        booking.setStatus("CANCELLED");

        // Change seat status from BOOKED to AVAILABLE
        seat.setStatus("AVAILABLE");

        // Release the Redis lock for that seat
        seatLockService.unlockSeat(seat.getId());

        // Save the seat and booking transactionally
        seatRepository.save(seat);
        return bookingRepository.save(booking);
    }
}
