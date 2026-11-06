package com.paycore.event.model;

/**
 * Standard domain event types across the PayCore platform.
 */
public enum EventType {
    PAYMENT_CREATED,
    PAYMENT_PROCESSING,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    PAYMENT_CANCELLED,
    PAYMENT_UNKNOWN,
    REFUND_CREATED,
    REFUND_PROCESSING,
    REFUND_SUCCESS,
    REFUND_FAILED,
    REFUND_UNKNOWN
}
