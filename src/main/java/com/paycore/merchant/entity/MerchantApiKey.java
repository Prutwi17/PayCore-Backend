package com.paycore.merchant.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "merchant_api_keys", indexes = {
        @Index(name = "idx_api_keys_hash", columnList = "key_hash"),
        @Index(name = "idx_api_keys_merchant", columnList = "merchant_id")
})
public class MerchantApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "key_prefix", nullable = false, length = 32)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true, length = 255)
    private String keyHash;

    @Column(name = "key_type", nullable = false, length = 30)
    private String keyType; // SECRET, PUBLISHABLE

    @Column(name = "environment", nullable = false, length = 20)
    private String environment; // TEST, LIVE

    @Column(name = "status", nullable = false, length = 30)
    private String status; // ACTIVE, REVOKED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public MerchantApiKey() {
    }

    public MerchantApiKey(Long merchantId, String keyPrefix, String keyHash, String keyType, String environment) {
        this.merchantId = merchantId;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.keyType = keyType;
        this.environment = environment;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
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
