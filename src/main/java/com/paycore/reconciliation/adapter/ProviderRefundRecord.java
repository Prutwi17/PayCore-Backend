package com.paycore.reconciliation.adapter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable external provider statement record representing a settled, failed, or pending refund.
 */
public record ProviderRefundRecord(
        String providerRefundId,
        String providerTransactionId,
        String requestReference,
        Long merchantId,
        BigDecimal amount,
        String currency,
        String status, // e.g., "SUCCESS", "FAILED", "PENDING"
        Instant settledAt,
        String rawResponseCode,
        String rawResponseMessage
) {
}
