package com.seatlock.backend.service;

import com.seatlock.backend.model.Seat;
import com.seatlock.backend.repository.EventRepository;
import com.seatlock.backend.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private SeatLockService seatLockService;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private SeatService seatService;

    private Seat sampleSeat;

    @BeforeEach
    void setUp() {
        sampleSeat = new Seat();
        sampleSeat.setEventId(1);
        sampleSeat.setSeatNumber("A1");
        sampleSeat.setStatus("AVAILABLE");
    }

    // --- getSeatsByEventId ---

    @Test
    void getSeatsByEventId_validEvent_returnsSeats() {
        when(eventRepository.existsById(1)).thenReturn(true);
        when(seatRepository.findByEventId(1)).thenReturn(List.of(sampleSeat));

        List<Seat> result = seatService.getSeatsByEventId(1);

        assertEquals(1, result.size());
        assertEquals("A1", result.get(0).getSeatNumber());
    }

    @Test
    void getSeatsByEventId_nullEventId_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.getSeatsByEventId(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getSeatsByEventId_negativeEventId_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.getSeatsByEventId(-5));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getSeatsByEventId_eventNotFound_throws404() {
        when(eventRepository.existsById(99)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.getSeatsByEventId(99));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // --- getSeatById ---

    @Test
    void getSeatById_validSeat_returnsSeat() {
        when(seatRepository.findById(10)).thenReturn(Optional.of(sampleSeat));

        Seat result = seatService.getSeatById(10);
        assertNotNull(result);
        assertEquals("A1", result.getSeatNumber());
    }

    @Test
    void getSeatById_invalidSeatId_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.getSeatById(0));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getSeatById_seatNotFound_throws404() {
        when(seatRepository.findById(999)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.getSeatById(999));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // --- createSeat ---

    @Test
    void createSeat_success() {
        when(eventRepository.existsById(1)).thenReturn(true);
        when(seatRepository.existsByEventIdAndSeatNumber(1, "A1")).thenReturn(false);
        when(seatRepository.saveAndFlush(any(Seat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Seat created = seatService.createSeat(sampleSeat);

        assertNotNull(created);
        assertEquals("A1", created.getSeatNumber());
        assertEquals("AVAILABLE", created.getStatus());
        verify(seatRepository).saveAndFlush(any(Seat.class));
    }

    @Test
    void createSeat_nullSeat_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.createSeat(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createSeat_nullEventId_throws400() {
        sampleSeat.setEventId(null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.createSeat(sampleSeat));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createSeat_blankSeatNumber_throws400() {
        sampleSeat.setSeatNumber("   ");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.createSeat(sampleSeat));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createSeat_eventNotFound_throws404() {
        when(eventRepository.existsById(1)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.createSeat(sampleSeat));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(seatRepository, never()).saveAndFlush(any());
    }

    @Test
    void createSeat_duplicateSeatNumber_throws409() {
        when(eventRepository.existsById(1)).thenReturn(true);
        when(seatRepository.existsByEventIdAndSeatNumber(1, "A1")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.createSeat(sampleSeat));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already exists"));
        verify(seatRepository, never()).saveAndFlush(any());
    }

    @Test
    void createSeat_dataIntegrityViolationException_throws409() {
        when(eventRepository.existsById(1)).thenReturn(true);
        when(seatRepository.existsByEventIdAndSeatNumber(1, "A1")).thenReturn(false);
        when(seatRepository.saveAndFlush(any(Seat.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry '1-A1'"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.createSeat(sampleSeat));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already exists"));
    }

    // --- generateSeats ---

    @Test
    void generateSeats_success() {
        when(eventRepository.existsById(1)).thenReturn(true);
        when(seatRepository.findByEventId(1)).thenReturn(Collections.emptyList());
        when(seatRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Seat> generated = seatService.generateSeats(1, 2, 3);

        assertEquals(6, generated.size());
        assertEquals("A1", generated.get(0).getSeatNumber());
        assertEquals("A2", generated.get(1).getSeatNumber());
        assertEquals("A3", generated.get(2).getSeatNumber());
        assertEquals("B1", generated.get(3).getSeatNumber());
        assertEquals("B2", generated.get(4).getSeatNumber());
        assertEquals("B3", generated.get(5).getSeatNumber());
        verify(seatRepository).saveAllAndFlush(anyList());
    }

    @Test
    void generateSeats_nullEventId_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.generateSeats(null, 2, 2));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void generateSeats_invalidRows_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.generateSeats(1, 0, 5));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void generateSeats_invalidSeatsPerRow_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.generateSeats(1, 5, -1));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void generateSeats_eventNotFound_throws404() {
        when(eventRepository.existsById(99)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.generateSeats(99, 2, 2));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(seatRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void generateSeats_alreadyGenerated_throws409() {
        when(eventRepository.existsById(1)).thenReturn(true);
        when(seatRepository.findByEventId(1)).thenReturn(List.of(sampleSeat));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.generateSeats(1, 2, 2));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already been generated"));
        verify(seatRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void generateSeats_dataIntegrityViolationException_throws409() {
        when(eventRepository.existsById(1)).thenReturn(true);
        when(seatRepository.findByEventId(1)).thenReturn(Collections.emptyList());
        when(seatRepository.saveAllAndFlush(anyList()))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry '1-A1'"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.generateSeats(1, 2, 2));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already exist"));
    }

    // --- lockSeat ---

    @Test
    void lockSeat_success() {
        when(seatRepository.findById(1)).thenReturn(Optional.of(sampleSeat));
        when(seatLockService.lockSeat(1)).thenReturn(true);
        when(seatRepository.saveAndFlush(any(Seat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Seat locked = seatService.lockSeat(1);

        assertEquals("LOCKED", locked.getStatus());
        assertNotNull(locked.getLockedUntil());
        verify(seatLockService).lockSeat(1);
    }

    @Test
    void lockSeat_invalidSeatId_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.lockSeat(0));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void lockSeat_seatNotFound_throws404() {
        when(seatRepository.findById(99)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.lockSeat(99));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void lockSeat_seatNotAvailable_throws409() {
        sampleSeat.setStatus("BOOKED");
        when(seatRepository.findById(1)).thenReturn(Optional.of(sampleSeat));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.lockSeat(1));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void lockSeat_redisLockFails_throws409() {
        when(seatRepository.findById(1)).thenReturn(Optional.of(sampleSeat));
        when(seatLockService.lockSeat(1)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> seatService.lockSeat(1));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }
}

