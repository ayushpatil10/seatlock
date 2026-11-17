package com.seatlock.backend.service;

import com.seatlock.backend.model.Booking;
import com.seatlock.backend.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingExpirySchedulerTest {

    @Mock
    private BookingService bookingService;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingExpiryScheduler scheduler;

    @Test
    void autoExpireBookings_findsExpiredAndUntimestampedBookings_expiresEach() {
        Booking oldBooking = new Booking(1, 1, 10, "PENDING", LocalDateTime.now().minusMinutes(15), "key-1");
        Booking nullTimeBooking = new Booking(2, 2, 10, "PENDING", null, "key-2");

        when(bookingRepository.findByStatusAndCreatedAtBeforeOrCreatedAtIsNull(eq("PENDING"), any(LocalDateTime.class)))
                .thenReturn(List.of(oldBooking, nullTimeBooking));

        scheduler.autoExpireBookings();

        verify(bookingRepository).findByStatusAndCreatedAtBeforeOrCreatedAtIsNull(eq("PENDING"), any(LocalDateTime.class));
        verify(bookingService).expireBooking(1);
        verify(bookingService).expireBooking(2);
    }

    @Test
    void autoExpireBookings_oneBookingThrows_continuesWithRemaining() {
        Booking failingBooking = new Booking(1, 1, 10, "PENDING", null, "key-1");
        Booking successfulBooking = new Booking(2, 2, 10, "PENDING", null, "key-2");

        when(bookingRepository.findByStatusAndCreatedAtBeforeOrCreatedAtIsNull(eq("PENDING"), any(LocalDateTime.class)))
                .thenReturn(List.of(failingBooking, successfulBooking));

        doThrow(new RuntimeException("Lock conflict")).when(bookingService).expireBooking(1);

        // Must not throw an unhandled exception
        scheduler.autoExpireBookings();

        verify(bookingService).expireBooking(1);
        verify(bookingService).expireBooking(2);
    }

    @Test
    void autoExpireBookings_noExpiredBookings_doesNothing() {
        when(bookingRepository.findByStatusAndCreatedAtBeforeOrCreatedAtIsNull(eq("PENDING"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.autoExpireBookings();

        verify(bookingRepository).findByStatusAndCreatedAtBeforeOrCreatedAtIsNull(eq("PENDING"), any(LocalDateTime.class));
        verify(bookingService, never()).expireBooking(anyInt());
    }
}

