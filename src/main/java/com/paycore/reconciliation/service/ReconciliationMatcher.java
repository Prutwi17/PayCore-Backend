package com.paycore.reconciliation.service;

import com.paycore.payment.entity.Payment;
import com.paycore.payment.entity.PaymentStatus;
import com.paycore.reconciliation.adapter.ProviderRefundRecord;
import com.paycore.reconciliation.adapter.ProviderTransactionRecord;
import com.paycore.reconciliation.entity.*;
import com.paycore.refund.entity.Refund;
import com.paycore.refund.entity.RefundStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Deterministic reconciliation matching engine enforcing the strict 6-step matching hierarchy.
 * Never uses fuzzy matching. Preserves UNKNOWN payment outcomes for manual review.
 */
@Component
public class ReconciliationMatcher {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationMatcher.class);

    /**
     * Reconciles internal payment records against provider transaction records.
     */
    public List<ReconciliationRecord> matchPayments(
            ReconciliationRun run,
            List<Payment> internalPayments,
            List<ProviderTransactionRecord> providerRecords
    ) {
        List<ReconciliationRecord> results = new ArrayList<>();

        // Group provider records by providerTransactionId and by requestReference (internal reference)
        Map<String, ProviderTransactionRecord> providerByTxId = new HashMap<>();
        Map<String, List<ProviderTransactionRecord>> providerByRef = new HashMap<>();

        for (ProviderTransactionRecord pr : providerRecords) {
            if (pr.providerTransactionId() != null) {
                providerByTxId.put(pr.providerTransactionId(), pr);
            }
            if (pr.requestReference() != null) {
                providerByRef.computeIfAbsent(pr.requestReference(), k -> new ArrayList<>()).add(pr);
            }
        }

        Set<String> matchedProviderTxIds = new HashSet<>();

        // 1. Process internal payments
        for (Payment payment : internalPayments) {
            String paymentRef = payment.getPaymentReference();
            String providerTxId = payment.getProviderTransactionId();

            // Detect duplicate provider records for this internal payment reference
            List<ProviderTransactionRecord> duplicates = providerByRef.get(paymentRef);
            if (duplicates != null && duplicates.size() > 1) {
                for (ProviderTransactionRecord dup : duplicates) {
                    matchedProviderTxIds.add(dup.providerTransactionId());
                    results.add(new ReconciliationRecord(
                            run,
                            RecordType.PAYMENT,
                            paymentRef,
                            dup.providerTransactionId(),
                            payment.getAmount(),
                            dup.amount(),
                            payment.getCurrency(),
                            payment.getStatus().name(),
                            dup.status(),
                            DiscrepancyType.DUPLICATE_PROVIDER_RECORD,
                            RecordStatus.REVIEW_REQUIRED
                    ));
                }
                continue;
            }

            // Match priority: 1. By Provider Transaction ID, 2. By Request Reference
            ProviderTransactionRecord providerRecord = null;
            if (providerTxId != null) {
                providerRecord = providerByTxId.get(providerTxId);
            }
            if (providerRecord == null && paymentRef != null && duplicates != null && duplicates.size() == 1) {
                providerRecord = duplicates.get(0);
            }

            if (providerRecord == null) {
                // Internal record exists, provider has no matching transaction
                results.add(new ReconciliationRecord(
                        run,
                        RecordType.PAYMENT,
                        paymentRef,
                        providerTxId,
                        payment.getAmount(),
                        null,
                        payment.getCurrency(),
                        payment.getStatus().name(),
                        null,
                        DiscrepancyType.MISSING_PROVIDER,
                        RecordStatus.UNMATCHED
                ));
                continue;
            }

            matchedProviderTxIds.add(providerRecord.providerTransactionId());

            // Compare amounts
            BigDecimal internalAmount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            BigDecimal providerAmount = providerRecord.amount() != null ? providerRecord.amount() : BigDecimal.ZERO;
            if (internalAmount.compareTo(providerAmount) != 0) {
                results.add(new ReconciliationRecord(
                        run,
                        RecordType.PAYMENT,
                        paymentRef,
                        providerRecord.providerTransactionId(),
                        internalAmount,
                        providerAmount,
                        payment.getCurrency(),
                        payment.getStatus().name(),
                        providerRecord.status(),
                        DiscrepancyType.AMOUNT_MISMATCH,
                        RecordStatus.REVIEW_REQUIRED
                ));
                continue;
            }

            // Compare currencies
            String internalCurrency = payment.getCurrency() != null ? payment.getCurrency().trim() : "";
            String providerCurrency = providerRecord.currency() != null ? providerRecord.currency().trim() : "";
            if (!internalCurrency.equalsIgnoreCase(providerCurrency)) {
                results.add(new ReconciliationRecord(
                        run,
                        RecordType.PAYMENT,
                        paymentRef,
                        providerRecord.providerTransactionId(),
                        internalAmount,
                        providerAmount,
                        internalCurrency,
                        payment.getStatus().name(),
                        providerRecord.status(),
                        DiscrepancyType.CURRENCY_MISMATCH,
                        RecordStatus.REVIEW_REQUIRED
                ));
                continue;
            }

            // Check for UNKNOWN payment outcomes
            if (payment.getStatus() == PaymentStatus.UNKNOWN) {
                results.add(new ReconciliationRecord(
                        run,
                        RecordType.PAYMENT,
                        paymentRef,
                        providerRecord.providerTransactionId(),
                        internalAmount,
                        providerAmount,
                        internalCurrency,
                        payment.getStatus().name(),
                        providerRecord.status(),
                        DiscrepancyType.UNKNOWN_OUTCOME,
                        RecordStatus.REVIEW_REQUIRED
                ));
                continue;
            }

            // Compare statuses using explicit mapping
            boolean statusMatches = isPaymentStatusEquivalent(payment.getStatus(), providerRecord.status());
            if (!statusMatches) {
                results.add(new ReconciliationRecord(
                        run,
                        RecordType.PAYMENT,
                        paymentRef,
                        providerRecord.providerTransactionId(),
                        internalAmount,
                        providerAmount,
                        internalCurrency,
                        payment.getStatus().name(),
                        providerRecord.status(),
                        DiscrepancyType.STATUS_MISMATCH,
                        RecordStatus.REVIEW_REQUIRED
                ));
                continue;
            }

            // All attributes matched cleanly
            results.add(new ReconciliationRecord(
                    run,
                    RecordType.PAYMENT,
                    paymentRef,
                    providerRecord.providerTransactionId(),
                    internalAmount,
                    providerAmount,
                    internalCurrency,
                    payment.getStatus().name(),
                    providerRecord.status(),
                    DiscrepancyType.MATCHED,
                    RecordStatus.MATCHED
            ));
        }

        // 2. Identify provider records missing from internal PayCore database
        for (ProviderTransactionRecord pr : providerRecords) {
            if (!matchedProviderTxIds.contains(pr.providerTransactionId())) {
                results.add(new ReconciliationRecord(
                        run,
                        RecordType.PAYMENT,
                        pr.requestReference(),
                        pr.providerTransactionId(),
                        null,
                        pr.amount(),
                        pr.currency(),
                        null,
                        pr.status(),
                        DiscrepancyType.MISSING_INTERNAL,
                        RecordStatus.UNMATCHED
                ));
            }
        }

        return results;
    }

    /**
     * Reconciles internal refund records against provider refund records.
     */
    public List<ReconciliationRecord> matchRefunds(
            ReconciliationRun run,
            List<Refund> internalRefunds,
            List<ProviderRefundRecord> providerRecords
    ) {
        List<ReconciliationRecord> results = new ArrayList<>();

        Map<String, ProviderRefundRecord> providerByRefundId = new HashMap<>();
        Map<String, List<ProviderRefundRecord>> providerByRef = new HashMap<>();

        for (ProviderRefundRecord pr : providerRecords) {
            if (pr.providerRefundId() != null) {
                providerByRefundId.put(pr.providerRefundId(), pr);
            }
            if (pr.requestReference() != null) {
                providerByRef.computeIfAbsent(pr.requestReference(), k -> new ArrayList<>()).add(pr);
            }
        }

        Set<String> matchedRefundIds = new HashSet<>();

        // 1. Process internal refunds
        for (Refund refund : internalRefunds) {
            String refundRef = refund.getRefundReference();
            String providerRefundId = refund.getProviderRefundId();

            List<ProviderRefundRecord> duplicates = providerByRef.get(refundRef);
            if (duplicates != null && duplicates.size() > 1) {
                for (ProviderRefundRecord dup : duplicates) {
                    matchedRefundIds.add(dup.providerRefundId());
                    results.add(new ReconciliationRecord(
                            run,
                            RecordType.REFUND,
                            refundRef,
                            dup.providerRefundId(),
                            refund.getAmount(),
                            dup.amount(),
                            refund.getCurrency(),
                            refund.getStatus().name(),
                            dup.status(),
                            DiscrepancyType.DUPLICATE_PROVIDER_RECORD,
                            RecordStatus.REVIEW_REQUIRED
                    ));
                }
                continue;
            }

            ProviderRefundRecord providerRecord = null;
            if (providerRefundId != null) {
                providerRecord = providerByRefundId.get(providerRefundId);
            }
            if (providerRecord == null && refundRef != null && duplicates != null && duplicates.size() == 1) {
                providerRecord = duplicates.get(0);
            }

            if (providerRecord == null) {
                results.add(new ReconciliationRecord(
                        run,
                        RecordType.REFUND,
                        refundRef,
                        providerRefundId,
                        refund.getAmount(),
                        null,
                        refund.getCurrency(),
                        refund.getStatus().name(),
                        null,
                        DiscrepancyType.MISSING_PROVIDER,
                        RecordStatus.UNMATCHED
                ));
                continue;
            }

            matchedRefundIds.add(providerRecord.providerRefundId());

            BigDecimal internalAmount = refund.getAmount() != null ? refund.getAmount() : BigDecimal.ZERO;
            BigDecimal providerAmount = providerRecord.amount() != null ? providerRecord.amount() : BigDecimal.ZERO;
            if (internalAmount.compareTo(providerAmount) != 0) {
                results.add(new ReconciliationRecord(
                        run,
                        RecordType.REFUND,
                        refundRef,
                        providerRecord.providerRefundId(),
                        internalAmount,
                        providerAmount,
                        refund.getCurrency(),
                        refund.getStatus().name(),
                        providerRecord.status(),
                        DiscrepancyType.AMOUNT_MISMATCH,
                        RecordStatus.REVIEW_REQUIRED
                ));
                continue;
            }

            String internalCurrency = refund.getCurrency() != null ? refund.getCurrency().trim() : "";
            String providerCurrency = providerRecord.currency() != null ? providerRecord.currency().trim() : "";
            if (!internalCurrency.equalsIgnoreCase(providerCurrency)) {
                results.add(new ReconciliationRecord(
                        run,
                        RecordType.REFUND,
                        refundRef,
                        providerRecord.providerRefundId(),
                        internalAmount,
                        providerAmount,
                        internalCurrency,
                        refund.getStatus().name(),
                        providerRecord.status(),
                        DiscrepancyType.CURRENCY_MISMATCH,
                        RecordStatus.REVIEW_REQUIRED
                ));
                continue;
            }

            if (refund.getStatus() == RefundStatus.UNKNOWN) {
                results.add(new ReconciliationRecord(
                        run,
                        RecordType.REFUND,
                        refundRef,
                        providerRecord.providerRefundId(),
                        internalAmount,
                        providerAmount,
                        internalCurrency,
                        refund.getStatus().name(),
                        providerRecord.status(),
                        DiscrepancyType.UNKNOWN_OUTCOME,
                        RecordStatus.REVIEW_REQUIRED
                ));
                continue;
            }

            boolean statusMatches = isRefundStatusEquivalent(refund.getStatus(), providerRecord.status());
            if (!statusMatches) {
                results.add(new ReconciliationRecord(
                        run,
                        RecordType.REFUND,
                        refundRef,
                        providerRecord.providerRefundId(),
                        internalAmount,
                        providerAmount,
                        internalCurrency,
                        refund.getStatus().name(),
                        providerRecord.status(),
                        DiscrepancyType.STATUS_MISMATCH,
                        RecordStatus.REVIEW_REQUIRED
                ));
                continue;
            }

            results.add(new ReconciliationRecord(
                    run,
                    RecordType.REFUND,
                    refundRef,
                    providerRecord.providerRefundId(),
                    internalAmount,
                    providerAmount,
                    internalCurrency,
                    refund.getStatus().name(),
                    providerRecord.status(),
                    DiscrepancyType.MATCHED,
                    RecordStatus.MATCHED
            ));
        }

        // 2. Identify provider refund records missing from internal database
        for (ProviderRefundRecord pr : providerRecords) {
            if (!matchedRefundIds.contains(pr.providerRefundId())) {
                results.add(new ReconciliationRecord(
                        run,
                        RecordType.REFUND,
                        pr.requestReference(),
                        pr.providerRefundId(),
                        null,
                        pr.amount(),
                        pr.currency(),
                        null,
                        pr.status(),
                        DiscrepancyType.MISSING_INTERNAL,
                        RecordStatus.UNMATCHED
                ));
            }
        }

        return results;
    }

    private boolean isPaymentStatusEquivalent(PaymentStatus internalStatus, String providerStatus) {
        if (internalStatus == null || providerStatus == null) {
            return false;
        }
        String pStatus = providerStatus.toUpperCase();
        return switch (internalStatus) {
            case SUCCESS -> pStatus.contains("SUCCESS") || pStatus.contains("APPROVED") || pStatus.contains("SETTLED");
            case FAILED -> pStatus.contains("FAIL") || pStatus.contains("DECLINE");
            case CANCELLED -> pStatus.contains("CANCEL") || pStatus.contains("VOID");
            case PROCESSING, CREATED, REQUIRES_ACTION -> pStatus.contains("PENDING") || pStatus.contains("ACTION");
            case AUTHORIZED -> pStatus.contains("AUTH") || pStatus.contains("APPROVED");
            case PARTIALLY_REFUNDED, REFUNDED -> pStatus.contains("SUCCESS") || pStatus.contains("REFUND") || pStatus.contains("SETTLED");
            case UNKNOWN -> false;
            default -> false;
        };
    }

    private boolean isRefundStatusEquivalent(RefundStatus internalStatus, String providerStatus) {
        if (internalStatus == null || providerStatus == null) {
            return false;
        }
        String pStatus = providerStatus.toUpperCase();
        return switch (internalStatus) {
            case SUCCESS -> pStatus.contains("SUCCESS") || pStatus.contains("APPROVED") || pStatus.contains("SETTLED");
            case FAILED -> pStatus.contains("FAIL") || pStatus.contains("DECLINE");
            case PROCESSING, REQUESTED -> pStatus.contains("PENDING") || pStatus.contains("PROCESS");
            case UNKNOWN -> false;
            default -> false;
        };
    }
}
