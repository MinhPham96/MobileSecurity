package com.example.minh.sensors;

public class User {
    private String name, email, password;
    private boolean hasDevice;

    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.hasDevice = false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isHasDevice() {
        return hasDevice;
    }

    public void setHasDevice(boolean hasDevice) {
        this.hasDevice = hasDevice;
    }
}
