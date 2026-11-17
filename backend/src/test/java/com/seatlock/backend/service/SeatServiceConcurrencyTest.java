package com.seatlock.backend.service;

import com.seatlock.backend.model.Seat;
import com.seatlock.backend.repository.EventRepository;
import com.seatlock.backend.repository.SeatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceConcurrencyTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private SeatLockService seatLockService;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private SeatService seatService;

    @Test
    void generateSeats_concurrentRequests_oneSucceedsOtherFailsWithConflict() throws Exception {
        int eventId = 1;
        when(eventRepository.existsById(eventId)).thenReturn(true);

        // First call sees empty seats, second sees existing seats
        AtomicBoolean firstCall = new AtomicBoolean(true);
        when(seatRepository.findByEventId(eventId)).thenAnswer(inv -> {
            if (firstCall.compareAndSet(true, false)) {
                return Collections.emptyList();
            } else {
                return List.of(new Seat(1, eventId, "A1", "AVAILABLE"));
            }
        });

        when(seatRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    seatService.generateSeats(eventId, 2, 5);
                    successCount.incrementAndGet();
                } catch (ResponseStatusException ex) {
                    if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        exceptions.add(ex);
                    }
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Fire both threads simultaneously
        startLatch.countDown();
        boolean completed = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrency test timed out");
        assertTrue(exceptions.isEmpty(), "Unexpected exceptions: " + exceptions);
        assertEquals(1, successCount.get(), "Exactly one generateSeats call must succeed");
        assertEquals(1, conflictCount.get(), "Competing generateSeats call must fail with 409 CONFLICT");
    }

    @Test
    void generateSeats_concurrentDatabaseUniqueConstraintViolation_handledAsConflict() throws Exception {
        int eventId = 2;
        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(seatRepository.findByEventId(eventId)).thenReturn(Collections.emptyList());

        // Simulate database unique constraint error on flush
        AtomicBoolean firstCall = new AtomicBoolean(true);
        when(seatRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> {
            if (firstCall.compareAndSet(true, false)) {
                return inv.getArgument(0);
            } else {
                throw new DataIntegrityViolationException("Duplicate entry '2-A1' for key 'uk_seats_event_seat_number'");
            }
        });

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    seatService.generateSeats(eventId, 1, 3);
                    successCount.incrementAndGet();
                } catch (ResponseStatusException ex) {
                    if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Throwable ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(1, conflictCount.get());
    }

    @Test
    void createSeat_concurrentDuplicateRequests_oneSucceedsOtherFailsWithConflict() throws Exception {
        int eventId = 3;
        String seatNumber = "C1";
        Seat seat1 = new Seat(null, eventId, seatNumber, "AVAILABLE");
        Seat seat2 = new Seat(null, eventId, seatNumber, "AVAILABLE");

        when(eventRepository.existsById(eventId)).thenReturn(true);

        AtomicBoolean first = new AtomicBoolean(true);
        when(seatRepository.existsByEventIdAndSeatNumber(eventId, seatNumber)).thenAnswer(inv -> !first.get());
        when(seatRepository.saveAndFlush(any(Seat.class))).thenAnswer(inv -> {
            if (first.compareAndSet(true, false)) {
                return inv.getArgument(0);
            } else {
                throw new DataIntegrityViolationException("Duplicate entry '3-C1' for key 'uk_seats_event_seat_number'");
            }
        });

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                seatService.createSeat(seat1);
                successCount.incrementAndGet();
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                    conflictCount.incrementAndGet();
                }
            } catch (Throwable ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                seatService.createSeat(seat2);
                successCount.incrementAndGet();
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                    conflictCount.incrementAndGet();
                }
            } catch (Throwable ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(1, conflictCount.get());
    }
}

