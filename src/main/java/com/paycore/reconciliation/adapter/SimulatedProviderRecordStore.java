package com.paycore.reconciliation.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Independent provider-side statement and transaction ledger.
 * Simulates external clearing house / card network ledger independent of PayCore's internal database.
 * Used by reconciliation to detect genuine differences (missing records, amount mismatch, duplicate provider events).
 */
@Component
public class SimulatedProviderRecordStore {

    private static final Logger log = LoggerFactory.getLogger(SimulatedProviderRecordStore.class);

    private final Map<String, ProviderTransactionRecord> transactions = new ConcurrentHashMap<>();
    private final Map<String, ProviderRefundRecord> refunds = new ConcurrentHashMap<>();

    public void recordPaymentTransaction(String providerTransactionId, String requestReference, Long merchantId,
                                         BigDecimal amount, String currency, String status,
                                         String rawCode, String rawMessage) {
        ProviderTransactionRecord record = new ProviderTransactionRecord(
                providerTransactionId,
                requestReference,
                merchantId,
                amount,
                currency != null ? currency.toUpperCase() : "USD",
                status != null ? status.toUpperCase() : "SUCCESS",
                Instant.now(),
                rawCode,
                rawMessage
        );
        transactions.put(providerTransactionId, record);
        log.debug("Recorded provider-side transaction [txId={}, ref={}, status={}]",
                providerTransactionId, requestReference, status);
    }

    public void recordRefundTransaction(String providerRefundId, String providerTransactionId, String requestReference,
                                        Long merchantId, BigDecimal amount, String currency, String status,
                                        String rawCode, String rawMessage) {
        ProviderRefundRecord record = new ProviderRefundRecord(
                providerRefundId,
                providerTransactionId,
                requestReference,
                merchantId,
                amount,
                currency != null ? currency.toUpperCase() : "USD",
                status != null ? status.toUpperCase() : "SUCCESS",
                Instant.now(),
                rawCode,
                rawMessage
        );
        refunds.put(providerRefundId, record);
        log.debug("Recorded provider-side refund [refId={}, reqRef={}, status={}]",
                providerRefundId, requestReference, status);
    }

    public void updatePaymentStatus(String providerTransactionId, String newStatus) {
        ProviderTransactionRecord existing = transactions.get(providerTransactionId);
        if (existing != null) {
            ProviderTransactionRecord updated = new ProviderTransactionRecord(
                    existing.providerTransactionId(),
                    existing.requestReference(),
                    existing.merchantId(),
                    existing.amount(),
                    existing.currency(),
                    newStatus != null ? newStatus.toUpperCase() : existing.status(),
                    existing.settledAt(),
                    existing.rawResponseCode(),
                    "Status updated on provider ledger: " + newStatus
            );
            transactions.put(providerTransactionId, updated);
        }
    }

    public List<ProviderTransactionRecord> queryTransactions(Instant from, Instant to, Long merchantId) {
        return transactions.values().stream()
                .filter(tx -> from == null || !tx.settledAt().isBefore(from))
                .filter(tx -> to == null || !tx.settledAt().isAfter(to))
                .filter(tx -> merchantId == null || Objects.equals(tx.merchantId(), merchantId))
                .toList();
    }

    public List<ProviderRefundRecord> queryRefunds(Instant from, Instant to, Long merchantId) {
        return refunds.values().stream()
                .filter(rf -> from == null || !rf.settledAt().isBefore(from))
                .filter(rf -> to == null || !rf.settledAt().isAfter(to))
                .filter(rf -> merchantId == null || Objects.equals(rf.merchantId(), merchantId))
                .toList();
    }

    public Optional<ProviderTransactionRecord> findTransactionById(String providerTransactionId) {
        return Optional.ofNullable(transactions.get(providerTransactionId));
    }

    public Optional<ProviderRefundRecord> findRefundById(String providerRefundId) {
        return Optional.ofNullable(refunds.get(providerRefundId));
    }

    /**
     * Test / Simulation hook: Injects custom provider records directly into the provider ledger
     * to simulate real-world settlement discrepancies (e.g., duplicate settlement, amount variance).
     */
    public void putTransactionRecord(ProviderTransactionRecord record) {
        transactions.put(record.providerTransactionId(), record);
    }

    public void putRefundRecord(ProviderRefundRecord record) {
        refunds.put(record.providerRefundId(), record);
    }

    public void clear() {
        transactions.clear();
        refunds.clear();
    }
}
