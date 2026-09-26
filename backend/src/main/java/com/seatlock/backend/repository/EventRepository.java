package com.seatlock.backend.repository;

import com.seatlock.backend.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Integer> {
}