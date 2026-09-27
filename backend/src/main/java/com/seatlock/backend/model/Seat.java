package com.seatlock.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer eventId;

    private String seatNumber;

    private String status;

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
}
