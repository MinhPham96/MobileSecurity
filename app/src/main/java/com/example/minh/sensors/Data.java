package com.example.minh.sensors;

import java.util.Date;

public class Data {
    private float peak;
    private Date uploadDate;

    public Data() {
    }

    public Data(float peak, Date uploadDate) {
        this.peak = peak;
        this.uploadDate = uploadDate;
    }

    public float getPeak() {
        return peak;
    }

    public void setPeak(float peak) {
        this.peak = peak;
    }

    public Date getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(Date uploadDate) {
        this.uploadDate = uploadDate;
    }
}
