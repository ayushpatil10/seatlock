package com.seatlock.backend.model;

public class Event {

    private int id;
    private String title;
    private String language;
    private String location;
    private String date;

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