package com.paycore.payment.provider;

import java.time.Instant;

/**
 * Standardized response contract returned by payment providers after a refund attempt.
 */
public class PaymentProviderRefundResponse {

    public enum Status {
        SUCCESS,
        FAILED,
        UNKNOWN
    }

    private final String providerRefundId;
    private final Status status;
    private final String failureCode;
    private final String failureMessage;
    private final Instant processedAt;

    private PaymentProviderRefundResponse(
            String providerRefundId,
            Status status,
            String failureCode,
            String failureMessage,
            Instant processedAt
    ) {
        this.providerRefundId = providerRefundId;
        this.status = status;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.processedAt = processedAt;
    }

    public static PaymentProviderRefundResponse success(String providerRefundId, String code, String message) {
        return new PaymentProviderRefundResponse(providerRefundId, Status.SUCCESS, code, message, Instant.now());
    }

    public static PaymentProviderRefundResponse failed(String providerRefundId, String failureCode, String failureMessage) {
        return new PaymentProviderRefundResponse(providerRefundId, Status.FAILED, failureCode, failureMessage, Instant.now());
    }

    public static PaymentProviderRefundResponse unknown(String providerRefundId, String failureCode, String failureMessage) {
        return new PaymentProviderRefundResponse(providerRefundId, Status.UNKNOWN, failureCode, failureMessage, Instant.now());
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isUnknown() {
        return status == Status.UNKNOWN;
    }

    public String getProviderRefundId() {
        return providerRefundId;
    }

    public Status getStatus() {
        return status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
