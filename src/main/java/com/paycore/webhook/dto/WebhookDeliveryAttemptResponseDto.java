package com.paycore.webhook.dto;

import com.paycore.webhook.entity.WebhookDeliveryAttempt;
import com.paycore.webhook.entity.WebhookDeliveryStatus;

import java.time.Instant;

public class WebhookDeliveryAttemptResponseDto {

    private Long id;
    private String attemptReference;
    private String eventReference;
    private String eventType;
    private String endpointReference;
    private String requestUrl;
    private Long merchantId;
    private int attemptNumber;
    private WebhookDeliveryStatus deliveryStatus;
    private Integer httpStatusCode;
    private String responseBody;
    private Long executionDurationMs;
    private Instant nextRetryAt;
    private String failureReason;
    private Instant attemptedAt;
    private Instant createdAt;

    public WebhookDeliveryAttemptResponseDto() {
    }

    public static WebhookDeliveryAttemptResponseDto fromEntity(WebhookDeliveryAttempt attempt) {
        WebhookDeliveryAttemptResponseDto dto = new WebhookDeliveryAttemptResponseDto();
        dto.setId(attempt.getId());
        dto.setAttemptReference(attempt.getAttemptReference());
        if (attempt.getWebhookEvent() != null) {
            dto.setEventReference(attempt.getWebhookEvent().getEventReference());
            dto.setEventType(attempt.getWebhookEvent().getEventType());
        }
        if (attempt.getEndpoint() != null) {
            dto.setEndpointReference(attempt.getEndpoint().getEndpointReference());
        }
        dto.setRequestUrl(attempt.getRequestUrl());
        dto.setMerchantId(attempt.getMerchantId());
        dto.setAttemptNumber(attempt.getAttemptNumber());
        dto.setDeliveryStatus(attempt.getDeliveryStatus());
        dto.setHttpStatusCode(attempt.getHttpStatusCode());
        dto.setResponseBody(attempt.getResponseBody());
        dto.setExecutionDurationMs(attempt.getExecutionDurationMs());
        dto.setNextRetryAt(attempt.getNextRetryAt());
        dto.setFailureReason(attempt.getFailureReason());
        dto.setAttemptedAt(attempt.getAttemptedAt());
        dto.setCreatedAt(attempt.getCreatedAt());
        return dto;
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

    public String getEventReference() {
        return eventReference;
    }

    public void setEventReference(String eventReference) {
        this.eventReference = eventReference;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEndpointReference() {
        return endpointReference;
    }

    public void setEndpointReference(String endpointReference) {
        this.endpointReference = endpointReference;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
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
}
