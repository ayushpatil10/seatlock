package com.seatlock.backend.repository;

import com.seatlock.backend.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Integer> {

    List<Seat> findByEventId(Integer eventId);
}
