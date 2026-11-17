package com.seatlock.backend.controller;

import com.seatlock.backend.model.Seat;
import com.seatlock.backend.service.SeatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SeatController.class)
class SeatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SeatService seatService;

    // --- GET /api/v1/events/{eventId}/seats ---

    @Test
    void getSeats_validEventId_returns200() throws Exception {
        Seat seat = new Seat(1, 10, "A1", "AVAILABLE");
        when(seatService.getSeatsByEventId(10)).thenReturn(List.of(seat));

        mockMvc.perform(get("/api/v1/events/10/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].seatNumber").value("A1"));

        verify(seatService).getSeatsByEventId(10);
    }

    @Test
    void getSeats_invalidEventId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/events/0/seats"))
                .andExpect(status().isBadRequest());

        verify(seatService, never()).getSeatsByEventId(any());
    }

    // --- POST /api/v1/events/{eventId}/seats ---

    @Test
    void createSeat_valid_returns200() throws Exception {
        Seat seat = new Seat(1, 10, "A1", "AVAILABLE");
        when(seatService.createSeat(any(Seat.class))).thenReturn(seat);

        String json = """
                {"seatNumber":"A1","status":"AVAILABLE"}
                """;

        mockMvc.perform(post("/api/v1/events/10/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatNumber").value("A1"));

        verify(seatService).createSeat(any(Seat.class));
    }

    @Test
    void createSeat_invalidEventId_returns400() throws Exception {
        String json = """
                {"seatNumber":"A1"}
                """;

        mockMvc.perform(post("/api/v1/events/-1/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(seatService, never()).createSeat(any());
    }

    @Test
    void createSeat_blankSeatNumber_returns400() throws Exception {
        String json = """
                {"seatNumber":"   "}
                """;

        mockMvc.perform(post("/api/v1/events/10/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(seatService, never()).createSeat(any());
    }

    @Test
    void createSeat_duplicateSeat_returns409() throws Exception {
        when(seatService.createSeat(any(Seat.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Seat already exists"));

        String json = """
                {"seatNumber":"A1"}
                """;

        mockMvc.perform(post("/api/v1/events/10/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict());
    }

    // --- POST /api/v1/events/{eventId}/seats/generate ---

    @Test
    void generateSeats_valid_returns200() throws Exception {
        Seat seat = new Seat(1, 10, "A1", "AVAILABLE");
        when(seatService.generateSeats(10, 2, 5)).thenReturn(List.of(seat));

        mockMvc.perform(post("/api/v1/events/10/seats/generate")
                        .param("rows", "2")
                        .param("seatsPerRow", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].seatNumber").value("A1"));

        verify(seatService).generateSeats(10, 2, 5);
    }

    @Test
    void generateSeats_invalidEventId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/events/0/seats/generate")
                        .param("rows", "2")
                        .param("seatsPerRow", "5"))
                .andExpect(status().isBadRequest());

        verify(seatService, never()).generateSeats(anyInt(), anyInt(), anyInt());
    }

    @Test
    void generateSeats_zeroRows_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/events/10/seats/generate")
                        .param("rows", "0")
                        .param("seatsPerRow", "5"))
                .andExpect(status().isBadRequest());

        verify(seatService, never()).generateSeats(anyInt(), anyInt(), anyInt());
    }

    @Test
    void generateSeats_negativeSeatsPerRow_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/events/10/seats/generate")
                        .param("rows", "2")
                        .param("seatsPerRow", "-1"))
                .andExpect(status().isBadRequest());

        verify(seatService, never()).generateSeats(anyInt(), anyInt(), anyInt());
    }

    @Test
    void generateSeats_alreadyGenerated_returns409() throws Exception {
        when(seatService.generateSeats(10, 2, 5))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Seats have already been generated"));

        mockMvc.perform(post("/api/v1/events/10/seats/generate")
                        .param("rows", "2")
                        .param("seatsPerRow", "5"))
                .andExpect(status().isConflict());
    }

    // --- PUT /api/v1/seats/{seatId}/lock ---

    @Test
    void lockSeat_valid_returns200() throws Exception {
        Seat seat = new Seat(1, 10, "A1", "LOCKED");
        when(seatService.lockSeat(1)).thenReturn(seat);

        mockMvc.perform(put("/api/v1/seats/1/lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        verify(seatService).lockSeat(1);
    }

    @Test
    void lockSeat_invalidSeatId_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/seats/0/lock"))
                .andExpect(status().isBadRequest());

        verify(seatService, never()).lockSeat(any());
    }
}

