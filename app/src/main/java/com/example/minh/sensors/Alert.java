package com.example.minh.sensors;

public class Alert {
    float startTime;
    float stopTime;
    float totalTime;
    String date;

    public Alert(float startTime, float stopTime, float totalTime, String date) {
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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
