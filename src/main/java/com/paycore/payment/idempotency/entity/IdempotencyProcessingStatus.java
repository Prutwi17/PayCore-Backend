package com.paycore.payment.idempotency.entity;

/**
 * Processing status of an idempotency record.
 */
public enum IdempotencyProcessingStatus {
    /**
     * Operation is actively in-flight with backend/provider.
     */
    PENDING,

    /**
     * Operation has completed and cached response is available.
     */
    COMPLETED,

    /**
     * Operation failed with an unrecoverable failure.
     */
    FAILED
}
