package com.example.minh.sensors;

public class Device {
    private String name, macAddress;
    private Alert alert;
    private boolean isOwned;

    public Device(String macAddress, Alert alert) {
        this.macAddress = macAddress;
        this.alert = alert;
    }

    public Device(String name, String macAddress, boolean isOwned) {
        this.name = name;
        this.macAddress = macAddress;
        this.isOwned = isOwned;
    }

    public Device() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public Alert getAlert() {
        return alert;
    }

    public void setAlert(Alert alert) {
        this.alert = alert;
    }

    public boolean isOwned() {
        return isOwned;
    }

    public void setOwned(boolean owned) {
        isOwned = owned;
    }
}
