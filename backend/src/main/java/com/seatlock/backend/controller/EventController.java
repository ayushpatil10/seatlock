package com.seatlock.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {

    @GetMapping("/api/v1/events")
    public String getEvents() {
        return "Hello from SeatLock!";
    }
}