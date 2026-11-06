package com.paycore.reconciliation.entity;

public enum DiscrepancyType {
    MATCHED,
    MISSING_INTERNAL,
    MISSING_PROVIDER,
    AMOUNT_MISMATCH,
    CURRENCY_MISMATCH,
    STATUS_MISMATCH,
    DUPLICATE_PROVIDER_RECORD,
    UNKNOWN_OUTCOME,
    REVIEW_REQUIRED
}
