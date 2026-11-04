package com.paycore.merchant.dto;

import java.time.Instant;

public class ApiKeyCreatedResponseDto {

    private Long id;
    private String keyPrefix;
    private String secretKey;
    private String keyType;
    private String environment;
    private String status;
    private Instant createdAt;
    private String warningMessage = "Please record your secret key immediately. For security reasons, it cannot be displayed or retrieved again.";

    public ApiKeyCreatedResponseDto() {
    }

    public ApiKeyCreatedResponseDto(Long id, String keyPrefix, String secretKey, String keyType, String environment, String status, Instant createdAt) {
        this.id = id;
        this.keyPrefix = keyPrefix;
        this.secretKey = secretKey;
        this.keyType = keyType;
        this.environment = environment;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public void setWarningMessage(String warningMessage) {
        this.warningMessage = warningMessage;
    }
}
