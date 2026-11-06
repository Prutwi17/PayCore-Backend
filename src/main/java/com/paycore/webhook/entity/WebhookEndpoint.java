package com.paycore.webhook.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(
        name = "webhook_endpoints",
        indexes = {
                @Index(name = "idx_webhook_endpoints_merchant", columnList = "merchant_id, status"),
                @Index(name = "idx_webhook_endpoints_ref", columnList = "endpoint_reference")
        }
)
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "endpoint_reference", nullable = false, unique = true, length = 64)
    private String endpointReference;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "url", nullable = false, length = 1024)
    private String url;

    @Column(name = "secret_hash", nullable = false, length = 255)
    private String secretHash;

    @Column(name = "secret_prefix", nullable = false, length = 16)
    private String secretPrefix = "whsec_";

    @Column(name = "secret_encrypted", nullable = false, length = 255)
    private String secretEncrypted;

    @Column(name = "subscribed_events", nullable = false, length = 1000)
    private String subscribedEvents = "*";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EndpointStatus status = EndpointStatus.ACTIVE;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "failure_count", nullable = false)
    private int failureCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version")
    private Long version = 0L;

    public WebhookEndpoint() {
    }

    public boolean isSubscribedTo(String eventType) {
        if (subscribedEvents == null || subscribedEvents.isBlank() || "*".equals(subscribedEvents.trim())) {
            return true;
        }
        List<String> events = Arrays.stream(subscribedEvents.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
        return events.contains("*") || events.contains(eventType);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEndpointReference() {
        return endpointReference;
    }

    public void setEndpointReference(String endpointReference) {
        this.endpointReference = endpointReference;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public String getSecretPrefix() {
        return secretPrefix;
    }

    public void setSecretPrefix(String secretPrefix) {
        this.secretPrefix = secretPrefix;
    }

    public String getSecretEncrypted() {
        return secretEncrypted;
    }

    public void setSecretEncrypted(String secretEncrypted) {
        this.secretEncrypted = secretEncrypted;
    }

    public String getSubscribedEvents() {
        return subscribedEvents;
    }

    public void setSubscribedEvents(String subscribedEvents) {
        this.subscribedEvents = subscribedEvents;
    }

    public EndpointStatus getStatus() {
        return status;
    }

    public void setStatus(EndpointStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
