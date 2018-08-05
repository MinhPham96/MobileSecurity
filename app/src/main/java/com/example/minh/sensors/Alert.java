package com.example.minh.sensors;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

public class Alert {
    float startTime;
    float stopTime;
    float totalTime;
    @ServerTimestamp
    Date date;

    public Alert(float startTime, float stopTime, float totalTime, Date date) {
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.totalTime = totalTime;
        this.date = date;
    }

    public Alert() { }

    public float getStartTime() {
        return startTime;
    }

    public void setStartTime(float startTime) {
        this.startTime = startTime;
    }

    public float getStopTime() {
        return stopTime;
    }

    public void setStopTime(float stopTime) {
        this.stopTime = stopTime;
    }

    public float getTotalTime() {
        return totalTime;
    }

    public void setTotalTime(float totalTime) {
        this.totalTime = totalTime;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }
}
