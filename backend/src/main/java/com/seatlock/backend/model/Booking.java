package com.seatlock.backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer seatId;

    private Integer eventId;

    private String status;

    private LocalDateTime createdAt;

    public Booking() {
    }

    public Booking(Integer id, Integer seatId, Integer eventId,
                   String status, LocalDateTime createdAt) {
        this.id = id;
        this.seatId = seatId;
        this.eventId = eventId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Integer getId() {
        return id;
    }

    public Integer getSeatId() {
        return seatId;
    }

    public Integer getEventId() {
        return eventId;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setSeatId(Integer seatId) {
        this.seatId = seatId;
    }

    public void setEventId(Integer eventId) {
        this.eventId = eventId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
