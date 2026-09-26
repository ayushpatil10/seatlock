package com.seatlock.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String title;
    private String language;
    private String location;
    private String date;

    public Event() {
    }

    public Event(int id, String title, String language, String location, String date) {
        this.id = id;
        this.title = title;
        this.language = language;
        this.location = location;
        this.date = date;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getLanguage() {
        return language;
    }

    public String getLocation() {
        return location;
    }

    public String getDate() {
        return date;
    }
}