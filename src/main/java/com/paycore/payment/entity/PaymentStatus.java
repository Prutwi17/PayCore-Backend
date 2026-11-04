package com.paycore.payment.entity;

public enum PaymentStatus {
    CREATED,
    PROCESSING,
    REQUIRES_ACTION,
    AUTHORIZED,
    SUCCESS,
    FAILED,
    CANCELLED,
    UNKNOWN
}
