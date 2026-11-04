package com.paycore.merchant.dto;

import com.paycore.merchant.entity.MerchantApiKey;

import java.time.Instant;

public class ApiKeyResponseDto {

    private Long id;
    private String keyPrefix;
    private String keyType;
    private String environment;
    private String status;
    private Instant createdAt;
    private Instant revokedAt;
    private Instant lastUsedAt;

    public ApiKeyResponseDto() {
    }

    public static ApiKeyResponseDto fromEntity(MerchantApiKey key) {
        ApiKeyResponseDto dto = new ApiKeyResponseDto();
        dto.setId(key.getId());
        dto.setKeyPrefix(key.getKeyPrefix());
        dto.setKeyType(key.getKeyType());
        dto.setEnvironment(key.getEnvironment());
        dto.setStatus(key.getStatus());
        dto.setCreatedAt(key.getCreatedAt());
        dto.setRevokedAt(key.getRevokedAt());
        dto.setLastUsedAt(key.getLastUsedAt());
        return dto;
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

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
