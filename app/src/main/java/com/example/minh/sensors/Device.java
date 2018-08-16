package com.example.minh.sensors;

public class Device {
    private String macAddress;

    private Alert alert;

    public Device(String macAddress, Alert alert) {
        this.macAddress = macAddress;
        this.alert = alert;
    }

    public Device() {
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
}
