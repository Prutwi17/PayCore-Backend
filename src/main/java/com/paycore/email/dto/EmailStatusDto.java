package com.paycore.email.dto;

import java.util.List;

public class EmailStatusDto {

    private boolean configured;
    private String provider;
    private String senderEmail;
    private String redirectUri;
    private List<String> missingProperties;

    public EmailStatusDto() {
    }

    public EmailStatusDto(boolean configured, String provider, String senderEmail, String redirectUri, List<String> missingProperties) {
        this.configured = configured;
        this.provider = provider;
        this.senderEmail = senderEmail;
        this.redirectUri = redirectUri;
        this.missingProperties = missingProperties;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public List<String> getMissingProperties() {
        return missingProperties;
    }

    public void setMissingProperties(List<String> missingProperties) {
        this.missingProperties = missingProperties;
    }
}
