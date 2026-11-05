package com.paycore.refund.entity;

/**
 * State machine statuses representing the lifecycle of a refund transaction.
 */
public enum RefundStatus {
    REQUESTED,
    PROCESSING,
    SUCCESS,
    FAILED,
    UNKNOWN
}
