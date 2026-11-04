package com.paycore.payment.dto;

import com.paycore.payment.entity.PaymentAttempt;
import com.paycore.payment.entity.PaymentAttemptStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Payment attempt telemetry and provider audit record")
public class PaymentAttemptResponseDto {

    private Long id;
    private Integer attemptNumber;
    private String provider;
    private String providerTransactionId;
    private PaymentAttemptStatus status;
    private String requestReference;
    private String responseCode;
    private String responseMessage;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;

    public PaymentAttemptResponseDto() {
    }

    public static PaymentAttemptResponseDto fromEntity(PaymentAttempt attempt) {
        PaymentAttemptResponseDto dto = new PaymentAttemptResponseDto();
        dto.setId(attempt.getId());
        dto.setAttemptNumber(attempt.getAttemptNumber());
        dto.setProvider(attempt.getProvider());
        dto.setProviderTransactionId(attempt.getProviderTransactionId());
        dto.setStatus(attempt.getStatus());
        dto.setRequestReference(attempt.getRequestReference());
        dto.setResponseCode(attempt.getResponseCode());
        dto.setResponseMessage(attempt.getResponseMessage());
        dto.setStartedAt(attempt.getStartedAt());
        dto.setCompletedAt(attempt.getCompletedAt());
        dto.setCreatedAt(attempt.getCreatedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public void setProviderTransactionId(String providerTransactionId) {
        this.providerTransactionId = providerTransactionId;
    }

    public PaymentAttemptStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentAttemptStatus status) {
        this.status = status;
    }

    public String getRequestReference() {
        return requestReference;
    }

    public void setRequestReference(String requestReference) {
        this.requestReference = requestReference;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
