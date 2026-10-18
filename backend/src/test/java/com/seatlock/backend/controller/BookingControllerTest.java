package com.seatlock.backend.controller;

import com.seatlock.backend.model.Booking;
import com.seatlock.backend.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    // --- seatId validation ---

    @Test
    void createBooking_nullSeatId_returns400() throws Exception {
        String json = """
                {"eventId":1,"idempotencyKey":"key-1"}
                """;
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(bookingService, never()).createBooking(any());
    }

    @Test
    void createBooking_zeroSeatId_returns400() throws Exception {
        String json = """
                {"seatId":0,"eventId":1,"idempotencyKey":"key-1"}
                """;
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(bookingService, never()).createBooking(any());
    }

    @Test
    void createBooking_negativeSeatId_returns400() throws Exception {
        String json = """
                {"seatId":-1,"eventId":1,"idempotencyKey":"key-1"}
                """;
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(bookingService, never()).createBooking(any());
    }

    // --- eventId validation ---

    @Test
    void createBooking_nullEventId_returns400() throws Exception {
        String json = """
                {"seatId":1,"idempotencyKey":"key-1"}
                """;
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(bookingService, never()).createBooking(any());
    }

    @Test
    void createBooking_negativeEventId_returns400() throws Exception {
        String json = """
                {"seatId":1,"eventId":-5,"idempotencyKey":"key-1"}
                """;
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(bookingService, never()).createBooking(any());
    }

    // --- idempotencyKey validation ---

    @Test
    void createBooking_nullIdempotencyKey_returns400() throws Exception {
        String json = """
                {"seatId":1,"eventId":1}
                """;
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(bookingService, never()).createBooking(any());
    }

    @Test
    void createBooking_blankIdempotencyKey_returns400() throws Exception {
        String json = """
                {"seatId":1,"eventId":1,"idempotencyKey":"   "}
                """;
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(bookingService, never()).createBooking(any());
    }

    // --- valid request ---

    @Test
    void createBooking_validRequest_delegatesToService() throws Exception {
        Booking saved = new Booking(1, 10, 5, "PENDING", LocalDateTime.now(), "key-abc");
        when(bookingService.createBooking(any(Booking.class))).thenReturn(saved);

        String json = """
                {"seatId":10,"eventId":5,"idempotencyKey":"key-abc"}
                """;
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(bookingService, times(1)).createBooking(any(Booking.class));
    }
}
