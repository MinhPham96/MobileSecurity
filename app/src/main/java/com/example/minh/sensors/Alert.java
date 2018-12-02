package com.example.minh.sensors;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

public class Alert {
    private String imageURL;
    @ServerTimestamp
    private Date date;
    private boolean checked;

    public Alert(String imageURL, Date date, boolean checked) {
        this.imageURL = imageURL;
        this.date = date;
        this.checked = checked;
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

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }
}
