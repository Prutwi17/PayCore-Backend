package com.paycore.reconciliation.service;

import com.paycore.merchant.entity.Merchant;
import com.paycore.payment.entity.Payment;
import com.paycore.payment.entity.PaymentMethodType;
import com.paycore.payment.entity.PaymentStatus;
import com.paycore.reconciliation.adapter.ProviderRefundRecord;
import com.paycore.reconciliation.adapter.ProviderTransactionRecord;
import com.paycore.reconciliation.entity.*;
import com.paycore.refund.entity.Refund;
import com.paycore.refund.entity.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Reconciliation Matcher Unit Tests")
class ReconciliationMatcherTest {

    private ReconciliationMatcher matcher;
    private ReconciliationRun dummyRun;
    private Merchant dummyMerchant;

    @BeforeEach
    void setUp() {
        matcher = new ReconciliationMatcher();
        dummyRun = new ReconciliationRun("run_test_01", RunType.FULL, "admin", Instant.now().minusSeconds(86400), Instant.now());
        dummyMerchant = new Merchant();
        dummyMerchant.setId(1L);
        dummyMerchant.setBusinessName("Acme Corp");
    }

    private Payment createPayment(String ref, String providerTxId, BigDecimal amount, String currency, PaymentStatus status) {
        Payment p = new Payment();
        p.setPaymentReference(ref);
        p.setProviderTransactionId(providerTxId);
        p.setMerchant(dummyMerchant);
        p.setAmount(amount);
        p.setCurrency(currency);
        p.setPaymentMethod(PaymentMethodType.CARD);
        p.setStatus(status);
        p.setProvider("SIMULATED");
        return p;
    }

    private Refund createRefund(String ref, String providerRefundId, BigDecimal amount, String currency, RefundStatus status) {
        Refund r = new Refund();
        r.setRefundReference(ref);
        r.setProviderRefundId(providerRefundId);
        r.setMerchant(dummyMerchant);
        r.setAmount(amount);
        r.setCurrency(currency);
        r.setReason("Customer request");
        r.setStatus(status);
        return r;
    }

    @Test
    @DisplayName("Should successfully match clean internal payment with provider record")
    void testCleanPaymentMatch() {
        Payment payment = createPayment("PAY-1001", "tx_1001", new BigDecimal("150.00"), "USD", PaymentStatus.SUCCESS);
        ProviderTransactionRecord providerRecord = new ProviderTransactionRecord(
                "tx_1001", "PAY-1001", 1L, new BigDecimal("150.00"), "USD", "SUCCESS", Instant.now(), "00", "Approved"
        );

        List<ReconciliationRecord> records = matcher.matchPayments(dummyRun, List.of(payment), List.of(providerRecord));

        assertEquals(1, records.size());
        ReconciliationRecord record = records.get(0);
        assertEquals(DiscrepancyType.MATCHED, record.getDiscrepancyType());
        assertEquals(RecordStatus.MATCHED, record.getStatus());
        assertEquals("PAY-1001", record.getInternalReference());
        assertEquals("tx_1001", record.getProviderReference());
    }

    @Test
    @DisplayName("Should detect internal payment missing from provider statements")
    void testMissingProviderRecord() {
        Payment payment = createPayment("PAY-1002", "tx_1002", new BigDecimal("99.99"), "USD", PaymentStatus.SUCCESS);

        List<ReconciliationRecord> records = matcher.matchPayments(dummyRun, List.of(payment), Collections.emptyList());

        assertEquals(1, records.size());
        ReconciliationRecord record = records.get(0);
        assertEquals(DiscrepancyType.MISSING_PROVIDER, record.getDiscrepancyType());
        assertEquals(RecordStatus.UNMATCHED, record.getStatus());
        assertEquals("PAY-1002", record.getInternalReference());
        assertNull(record.getProviderAmount());
    }

    @Test
    @DisplayName("Should detect provider transaction missing from internal records")
    void testMissingInternalRecord() {
        ProviderTransactionRecord providerRecord = new ProviderTransactionRecord(
                "tx_ghost", "UNKNOWN_REF", 1L, new BigDecimal("49.00"), "USD", "SUCCESS", Instant.now(), "00", "Approved"
        );

        List<ReconciliationRecord> records = matcher.matchPayments(dummyRun, Collections.emptyList(), List.of(providerRecord));

        assertEquals(1, records.size());
        ReconciliationRecord record = records.get(0);
        assertEquals(DiscrepancyType.MISSING_INTERNAL, record.getDiscrepancyType());
        assertEquals(RecordStatus.UNMATCHED, record.getStatus());
        assertEquals("tx_ghost", record.getProviderReference());
        assertNull(record.getInternalAmount());
    }

    @Test
    @DisplayName("Should detect amount mismatch between internal payment and provider")
    void testAmountMismatch() {
        Payment payment = createPayment("PAY-1003", "tx_1003", new BigDecimal("100.00"), "USD", PaymentStatus.SUCCESS);
        ProviderTransactionRecord providerRecord = new ProviderTransactionRecord(
                "tx_1003", "PAY-1003", 1L, new BigDecimal("90.00"), "USD", "SUCCESS", Instant.now(), "00", "Approved"
        );

        List<ReconciliationRecord> records = matcher.matchPayments(dummyRun, List.of(payment), List.of(providerRecord));

        assertEquals(1, records.size());
        ReconciliationRecord record = records.get(0);
        assertEquals(DiscrepancyType.AMOUNT_MISMATCH, record.getDiscrepancyType());
        assertEquals(RecordStatus.REVIEW_REQUIRED, record.getStatus());
        assertEquals(new BigDecimal("100.00"), record.getInternalAmount());
        assertEquals(new BigDecimal("90.00"), record.getProviderAmount());
    }

    @Test
    @DisplayName("Should detect currency mismatch between internal and provider records")
    void testCurrencyMismatch() {
        Payment payment = createPayment("PAY-1004", "tx_1004", new BigDecimal("50.00"), "EUR", PaymentStatus.SUCCESS);
        ProviderTransactionRecord providerRecord = new ProviderTransactionRecord(
                "tx_1004", "PAY-1004", 1L, new BigDecimal("50.00"), "USD", "SUCCESS", Instant.now(), "00", "Approved"
        );

        List<ReconciliationRecord> records = matcher.matchPayments(dummyRun, List.of(payment), List.of(providerRecord));

        assertEquals(1, records.size());
        ReconciliationRecord record = records.get(0);
        assertEquals(DiscrepancyType.CURRENCY_MISMATCH, record.getDiscrepancyType());
        assertEquals(RecordStatus.REVIEW_REQUIRED, record.getStatus());
    }

    @Test
    @DisplayName("Should detect status mismatch (e.g. internal FAILED but provider SUCCESS)")
    void testStatusMismatch() {
        Payment payment = createPayment("PAY-1005", "tx_1005", new BigDecimal("75.00"), "USD", PaymentStatus.FAILED);
        ProviderTransactionRecord providerRecord = new ProviderTransactionRecord(
                "tx_1005", "PAY-1005", 1L, new BigDecimal("75.00"), "USD", "SUCCESS", Instant.now(), "00", "Approved"
        );

        List<ReconciliationRecord> records = matcher.matchPayments(dummyRun, List.of(payment), List.of(providerRecord));

        assertEquals(1, records.size());
        ReconciliationRecord record = records.get(0);
        assertEquals(DiscrepancyType.STATUS_MISMATCH, record.getDiscrepancyType());
        assertEquals(RecordStatus.REVIEW_REQUIRED, record.getStatus());
    }

    @Test
    @DisplayName("Should detect duplicate provider records referencing same internal payment")
    void testDuplicateProviderRecords() {
        Payment payment = createPayment("PAY-1006", "tx_1006_a", new BigDecimal("120.00"), "USD", PaymentStatus.SUCCESS);
        ProviderTransactionRecord recA = new ProviderTransactionRecord(
                "tx_1006_a", "PAY-1006", 1L, new BigDecimal("120.00"), "USD", "SUCCESS", Instant.now(), "00", "Approved"
        );
        ProviderTransactionRecord recB = new ProviderTransactionRecord(
                "tx_1006_b", "PAY-1006", 1L, new BigDecimal("120.00"), "USD", "SUCCESS", Instant.now(), "00", "Approved"
        );

        List<ReconciliationRecord> records = matcher.matchPayments(dummyRun, List.of(payment), List.of(recA, recB));

        assertEquals(2, records.size());
        assertTrue(records.stream().allMatch(r -> r.getDiscrepancyType() == DiscrepancyType.DUPLICATE_PROVIDER_RECORD));
        assertTrue(records.stream().allMatch(r -> r.getStatus() == RecordStatus.REVIEW_REQUIRED));
    }

    @Test
    @DisplayName("Should preserve UNKNOWN payment outcome as UNKNOWN_OUTCOME requiring review and never guess")
    void testPreserveUnknownOutcome() {
        Payment payment = createPayment("PAY-1007", "tx_1007", new BigDecimal("200.00"), "USD", PaymentStatus.UNKNOWN);
        ProviderTransactionRecord providerRecord = new ProviderTransactionRecord(
                "tx_1007", "PAY-1007", 1L, new BigDecimal("200.00"), "USD", "SUCCESS", Instant.now(), "00", "Approved"
        );

        List<ReconciliationRecord> records = matcher.matchPayments(dummyRun, List.of(payment), List.of(providerRecord));

        assertEquals(1, records.size());
        ReconciliationRecord record = records.get(0);
        assertEquals(DiscrepancyType.UNKNOWN_OUTCOME, record.getDiscrepancyType());
        assertEquals(RecordStatus.REVIEW_REQUIRED, record.getStatus());
    }

    @Test
    @DisplayName("Should match clean refund record with provider refund statement")
    void testCleanRefundMatch() {
        Refund refund = createRefund("REF-2001", "reftx_2001", new BigDecimal("40.00"), "USD", RefundStatus.SUCCESS);
        ProviderRefundRecord providerRecord = new ProviderRefundRecord(
                "reftx_2001", "tx_orig", "REF-2001", 1L, new BigDecimal("40.00"), "USD", "SUCCESS", Instant.now(), "00", "Approved"
        );

        List<ReconciliationRecord> records = matcher.matchRefunds(dummyRun, List.of(refund), List.of(providerRecord));

        assertEquals(1, records.size());
        ReconciliationRecord record = records.get(0);
        assertEquals(RecordType.REFUND, record.getRecordType());
        assertEquals(DiscrepancyType.MATCHED, record.getDiscrepancyType());
        assertEquals(RecordStatus.MATCHED, record.getStatus());
        assertEquals("REF-2001", record.getInternalReference());
        assertEquals("reftx_2001", record.getProviderReference());
    }

    @Test
    @DisplayName("Should detect refund status mismatch when provider failed refund")
    void testRefundStatusMismatch() {
        Refund refund = createRefund("REF-2002", "reftx_2002", new BigDecimal("25.00"), "USD", RefundStatus.SUCCESS);
        ProviderRefundRecord providerRecord = new ProviderRefundRecord(
                "reftx_2002", "tx_orig", "REF-2002", 1L, new BigDecimal("25.00"), "USD", "FAILED", Instant.now(), "51", "Insufficient funds"
        );

        List<ReconciliationRecord> records = matcher.matchRefunds(dummyRun, List.of(refund), List.of(providerRecord));

        assertEquals(1, records.size());
        ReconciliationRecord record = records.get(0);
        assertEquals(RecordType.REFUND, record.getRecordType());
        assertEquals(DiscrepancyType.STATUS_MISMATCH, record.getDiscrepancyType());
        assertEquals(RecordStatus.REVIEW_REQUIRED, record.getStatus());
    }
}
