package com.seatlock.backend.model;

import jakarta.persistence.*;

@Entity
@Table(
    name = "seats",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_seats_event_seat_number", columnNames = {"event_id", "seat_number"})
    }
)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "event_id")
    private Integer eventId;

    @Column(name = "seat_number")
    private String seatNumber;

    private String status;

    private java.time.LocalDateTime lockedUntil;

    public Seat() {
    }

    public Seat(Integer id, Integer eventId, String seatNumber, String status) {
        this.id = id;
        this.eventId = eventId;
        this.seatNumber = seatNumber;
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public Integer getEventId() {
        return eventId;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setEventId(Integer eventId) {
        this.eventId = eventId;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public java.time.LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(java.time.LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
}
