package com.paycore.auth.dto;

public class AdminInitiateSetupRequest {

    private String setupKey;

    public AdminInitiateSetupRequest() {
    }

    public AdminInitiateSetupRequest(String setupKey) {
        this.setupKey = setupKey;
    }

    public String getSetupKey() {
        return setupKey;
    }

    public void setSetupKey(String setupKey) {
        this.setupKey = setupKey;
    }
}
