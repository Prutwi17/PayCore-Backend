package com.paycore.webhook.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "webhook_delivery_attempts",
        indexes = {
                @Index(name = "idx_deliv_merchant_status", columnList = "merchant_id, delivery_status"),
                @Index(name = "idx_deliv_next_retry", columnList = "delivery_status, next_retry_at"),
                @Index(name = "idx_deliv_ref", columnList = "attempt_reference"),
                @Index(name = "idx_deliv_event", columnList = "webhook_event_id"),
                @Index(name = "idx_deliv_endpoint", columnList = "endpoint_id")
        }
)
public class WebhookDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_reference", nullable = false, unique = true, length = 64)
    private String attemptReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "webhook_event_id", nullable = false)
    private WebhookEvent webhookEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private WebhookEndpoint endpoint;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 32)
    private WebhookDeliveryStatus deliveryStatus = WebhookDeliveryStatus.PENDING;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "request_url", nullable = false, length = 1024)
    private String requestUrl;

    @Lob
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Lob
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Version
    @Column(name = "version")
    private Long version = 0L;

    public WebhookDeliveryAttempt() {
    }

    public WebhookDeliveryAttempt(String attemptReference, WebhookEvent webhookEvent, WebhookEndpoint endpoint, Long merchantId, int attemptNumber, String requestUrl) {
        this.attemptReference = attemptReference;
        this.webhookEvent = webhookEvent;
        this.endpoint = endpoint;
        this.merchantId = merchantId;
        this.attemptNumber = attemptNumber;
        this.requestUrl = requestUrl;
        this.deliveryStatus = WebhookDeliveryStatus.PENDING;
        this.attemptedAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAttemptReference() {
        return attemptReference;
    }

    public void setAttemptReference(String attemptReference) {
        this.attemptReference = attemptReference;
    }

    public WebhookEvent getWebhookEvent() {
        return webhookEvent;
    }

    public void setWebhookEvent(WebhookEvent webhookEvent) {
        this.webhookEvent = webhookEvent;
    }

    public WebhookEndpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(WebhookEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public WebhookDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(WebhookDeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Long getExecutionDurationMs() {
        return executionDurationMs;
    }

    public void setExecutionDurationMs(Long executionDurationMs) {
        this.executionDurationMs = executionDurationMs;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(Instant attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
