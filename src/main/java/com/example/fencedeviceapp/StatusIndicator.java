package com.example.fencedeviceapp;

public class StatusIndicator {
    private final String label;
    private final boolean status;

    public StatusIndicator(String label, boolean status) {
        this.label = label;
        this.status = status;
    }

    public String getLabel() {
        return label;
    }

    public boolean isStatus() {
        return status;
    }
}