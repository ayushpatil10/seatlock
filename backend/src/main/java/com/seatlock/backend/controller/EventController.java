package com.seatlock.backend.controller;

import com.seatlock.backend.model.Event;
import com.seatlock.backend.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/api/v1/events")
    public List<Event> getEvents() {
        return eventService.getAllEvents();
    }

    @GetMapping("/api/v1/events/{eventId}")
    public Event getEvent(@PathVariable int eventId) {
        return eventService.getEventById(eventId);
    }
}