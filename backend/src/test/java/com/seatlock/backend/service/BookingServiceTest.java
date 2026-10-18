package com.seatlock.backend.service;

import com.seatlock.backend.model.Booking;
import com.seatlock.backend.model.Seat;
import com.seatlock.backend.repository.BookingRepository;
import com.seatlock.backend.repository.EventRepository;
import com.seatlock.backend.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private SeatLockService seatLockService;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private BookingService bookingService;

    private Booking validBooking;
    private Seat availableSeat;

    @BeforeEach
    void setUp() {
        validBooking = new Booking();
        validBooking.setSeatId(1);
        validBooking.setEventId(10);
        validBooking.setIdempotencyKey("idem-key-1");

        availableSeat = new Seat(1, 10, "A1", "AVAILABLE");
    }

    // --- createBooking tests ---

    @Test
    void createBooking_seatNotFound_throws404() {
        when(bookingRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.empty());
        when(seatRepository.findByIdForUpdate(1)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.createBooking(validBooking));

        assertEquals(404, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Seat not found"));
    }

    @Test
    void createBooking_eventNotFound_throws404() {
        when(bookingRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.empty());
        when(seatRepository.findByIdForUpdate(1)).thenReturn(Optional.of(availableSeat));
        when(eventRepository.existsById(10)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.createBooking(validBooking));

        assertEquals(404, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Event not found"));
    }

    @Test
    void createBooking_seatNotBelongToEvent_throws400() {
        validBooking.setEventId(99); // different from seat's eventId (10)
        when(bookingRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.empty());
        when(seatRepository.findByIdForUpdate(1)).thenReturn(Optional.of(availableSeat));
        when(eventRepository.existsById(99)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.createBooking(validBooking));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Seat does not belong to this event"));
    }

    @Test
    void createBooking_seatAlreadyBooked_throws409() {
        availableSeat.setStatus("BOOKED");
        when(bookingRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.empty());
        when(seatRepository.findByIdForUpdate(1)).thenReturn(Optional.of(availableSeat));
        when(eventRepository.existsById(10)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.createBooking(validBooking));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Seat is already booked"));
    }

    @Test
    void createBooking_redisLockFails_throws409() {
        when(bookingRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.empty());
        when(seatRepository.findByIdForUpdate(1)).thenReturn(Optional.of(availableSeat));
        when(eventRepository.existsById(10)).thenReturn(true);
        when(seatLockService.lockSeat(1)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.createBooking(validBooking));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Seat is already locked"));
    }

    @Test
    void createBooking_happyPath_returnsPendingBooking() {
        when(bookingRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.empty());
        when(seatRepository.findByIdForUpdate(1)).thenReturn(Optional.of(availableSeat));
        when(eventRepository.existsById(10)).thenReturn(true);
        when(seatLockService.lockSeat(1)).thenReturn(true);

        Booking savedBooking = new Booking(1, 1, 10, "PENDING", null, "idem-key-1");
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenReturn(savedBooking);

        Booking result = bookingService.createBooking(validBooking);

        assertEquals("PENDING", result.getStatus());
        assertEquals(1, result.getId());
        verify(seatRepository).save(availableSeat);
        assertEquals("BOOKED", availableSeat.getStatus());
    }

    // --- getBookingById tests ---

    @Test
    void getBookingById_notFound_throws404() {
        when(bookingRepository.findById(999)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.getBookingById(999));

        assertEquals(404, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Booking not found"));
    }

    // --- cancelBooking tests ---

    @Test
    void cancelBooking_alreadyCancelled_throws409() {
        Booking cancelled = new Booking(1, 1, 10, "CANCELLED", null, "key-x");
        when(bookingRepository.findByIdForUpdate(1)).thenReturn(Optional.of(cancelled));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.cancelBooking(1));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Booking is already cancelled"));
    }
}
