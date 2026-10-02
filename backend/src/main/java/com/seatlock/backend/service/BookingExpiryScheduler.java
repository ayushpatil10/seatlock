package com.seatlock.backend.service;

import com.seatlock.backend.model.Booking;
import com.seatlock.backend.repository.BookingRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class BookingExpiryScheduler {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;

    public BookingExpiryScheduler(BookingService bookingService, BookingRepository bookingRepository) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
    }

    @Scheduled(fixedRate = 60000)
    public void autoExpireBookings() {
        LocalDateTime tenMinsAgo = LocalDateTime.now().minusMinutes(10);
        List<Booking> expiredBookings = bookingRepository.findByStatusAndCreatedAtBefore("PENDING", tenMinsAgo);
        
        for (Booking b : expiredBookings) {
            try {
                // Call the transactional expireBooking via proxy
                bookingService.expireBooking(b.getId());
            } catch (Exception e) {
                // Log and gracefully continue
                System.err.println("Failed to automatically expire booking " + b.getId() + ": " + e.getMessage());
            }
        }
    }
}
