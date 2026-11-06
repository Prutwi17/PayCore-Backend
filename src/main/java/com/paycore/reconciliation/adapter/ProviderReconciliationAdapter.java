package com.paycore.reconciliation.adapter;

import java.time.Instant;
import java.util.List;

/**
 * Abstraction for interacting with downstream provider/bank settlement reports and statement extracts.
 * Enables modular and extensible integration of real banking partners, payment gateways, and simulators.
 */
public interface ProviderReconciliationAdapter {

    /**
     * Unique identifier matching the payment provider name (e.g., "SIMULATED").
     */
    String getProviderName();

    /**
     * Extracts all transaction statement records recorded on the provider side within the time window.
     */
    List<ProviderTransactionRecord> fetchTransactionRecords(Instant from, Instant to, Long merchantId);

    /**
     * Extracts all refund statement records recorded on the provider side within the time window.
     */
    List<ProviderRefundRecord> fetchRefundRecords(Instant from, Instant to, Long merchantId);
}
