package com.seatlock.backend.service;

import com.seatlock.backend.model.Seat;
import com.seatlock.backend.repository.EventRepository;
import com.seatlock.backend.repository.SeatRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class SeatService {

    private final SeatRepository seatRepository;
    private final SeatLockService seatLockService;
    private final EventRepository eventRepository;

    public SeatService(
            SeatRepository seatRepository,
            SeatLockService seatLockService,
            EventRepository eventRepository) {
        this.seatRepository = seatRepository;
        this.seatLockService = seatLockService;
        this.eventRepository = eventRepository;
    }

    public List<Seat> getSeatsByEventId(Integer eventId) {
        if (eventId == null || eventId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId must be a positive integer");
        }
        if (!eventRepository.existsById(eventId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found with ID " + eventId);
        }
        return seatRepository.findByEventId(eventId);
    }

    public Seat getSeatById(Integer seatId) {
        if (seatId == null || seatId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seatId must be a positive integer");
        }
        return seatRepository.findById(seatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found with ID " + seatId));
    }

    @Transactional
    public Seat createSeat(Seat seat) {
        if (seat == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat must not be null");
        }
        if (seat.getEventId() == null || seat.getEventId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId must be a positive integer");
        }
        if (seat.getSeatNumber() == null || seat.getSeatNumber().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seatNumber must not be blank");
        }

        seat.setSeatNumber(seat.getSeatNumber().trim());

        if (!eventRepository.existsById(seat.getEventId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found with ID " + seat.getEventId());
        }

        if (seatRepository.existsByEventIdAndSeatNumber(seat.getEventId(), seat.getSeatNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seat " + seat.getSeatNumber() + " already exists for event ID " + seat.getEventId());
        }

        if (seat.getStatus() == null || seat.getStatus().trim().isEmpty()) {
            seat.setStatus("AVAILABLE");
        }

        try {
            return seatRepository.saveAndFlush(seat);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seat " + seat.getSeatNumber() + " already exists for event ID " + seat.getEventId(), e);
        }
    }

    @Transactional
    public List<Seat> generateSeats(Integer eventId, int rows, int seatsPerRow) {
        if (eventId == null || eventId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId must be a positive integer");
        }
        if (rows <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rows must be a positive integer");
        }
        if (seatsPerRow <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seatsPerRow must be a positive integer");
        }
        if (!eventRepository.existsById(eventId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found with ID " + eventId);
        }

        // Idempotency check: if seats already exist for this event, reject to prevent duplicates
        if (!seatRepository.findByEventId(eventId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seats have already been generated for event ID " + eventId);
        }

        List<Seat> seats = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            char rowName = (char) ('A' + row);
            for (int number = 1; number <= seatsPerRow; number++) {
                Seat seat = new Seat();
                seat.setEventId(eventId);
                seat.setSeatNumber(rowName + String.valueOf(number));
                seat.setStatus("AVAILABLE");
                seats.add(seat);
            }
        }

        try {
            return seatRepository.saveAllAndFlush(seats);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Concurrent seat generation conflict: seats already exist for event ID " + eventId, e);
        }
    }

    @Transactional
    public Seat lockSeat(Integer seatId) {
        if (seatId == null || seatId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seatId must be a positive integer");
        }

        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found with ID " + seatId));

        // Release an expired lock
        if ("LOCKED".equals(seat.getStatus())
                && seat.getLockedUntil() != null
                && seat.getLockedUntil().isBefore(java.time.LocalDateTime.now())) {

            seat.setStatus("AVAILABLE");
            seat.setLockedUntil(null);
            seatRepository.saveAndFlush(seat);
        }

        // Check availability after releasing an expired lock
        if (!"AVAILABLE".equals(seat.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat is not available");
        }

        boolean locked = seatLockService.lockSeat(seatId);

        if (!locked) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Seat is currently being locked by another user"
            );
        }

        seat.setStatus("LOCKED");
        seat.setLockedUntil(
                java.time.LocalDateTime.now().plusMinutes(10)
        );

        return seatRepository.saveAndFlush(seat);
    }
}
