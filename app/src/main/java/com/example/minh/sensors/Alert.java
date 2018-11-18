package com.example.minh.sensors;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

public class Alert {
    String imageURL;
    @ServerTimestamp
    Date date;

    public Alert(String imageURL, Date date) {
        this.imageURL = imageURL;
        this.date = date;
    }

    public Alert() {
    }

    public String getImageURL() {
        return imageURL;
    }

    public void setImageURL(String imageURL) {
        this.imageURL = imageURL;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }
}
