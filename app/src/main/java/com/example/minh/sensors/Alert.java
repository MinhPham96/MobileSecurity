package com.example.minh.sensors;

public class Alert {
    String startTime;
    String endTime;
    String timeGap;

    public Alert(String startTime, String endTime, String timeGap) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.timeGap = timeGap;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getTimeGap() {
        return timeGap;
    }

    public void setTimeGap(String timeGap) {
        this.timeGap = timeGap;
    }
}
