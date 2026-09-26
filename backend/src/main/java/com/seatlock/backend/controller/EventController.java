package com.seatlock.backend.controller;

import com.seatlock.backend.model.Event;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EventController {

    private final List<Event> events = List.of(
            new Event(
                    1,
                    "Avengers: Doomsday",
                    "English",
                    "PVR Phoenix Mall",
                    "2026-09-25"
            ),
            new Event(
                    2,
                    "Dhurandhar",
                    "Hindi",
                    "INOX Mall",
                    "2026-09-26"
            )
    );

    @GetMapping("/api/v1/events")
    public List<Event> getEvents() {
        return events;
    }

    @GetMapping("/api/v1/events/{eventId}")
    public Event getEvent(@PathVariable int eventId) {

        return events.stream()
                .filter(event -> event.getId() == eventId)
                .findFirst()
                .orElseThrow();
    }
}