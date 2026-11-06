package com.paycore.reconciliation.adapter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable external provider statement record representing a settled, failed, or pending payment transaction.
 */
public record ProviderTransactionRecord(
        String providerTransactionId,
        String requestReference,
        Long merchantId,
        BigDecimal amount,
        String currency,
        String status, // e.g., "SUCCESS", "FAILED", "CANCELLED", "PENDING"
        Instant settledAt,
        String rawResponseCode,
        String rawResponseMessage
) {
}
