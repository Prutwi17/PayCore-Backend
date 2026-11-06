package com.paycore.reconciliation.service;

import com.paycore.common.exception.DuplicateResourceException;
import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.audit.service.AuditLogService;
import com.paycore.merchant.entity.Merchant;
import com.paycore.payment.entity.Payment;
import com.paycore.payment.entity.PaymentMethodType;
import com.paycore.payment.entity.PaymentStatus;
import com.paycore.payment.repository.PaymentRepository;
import com.paycore.reconciliation.adapter.ProviderReconciliationAdapter;
import com.paycore.reconciliation.adapter.ProviderTransactionRecord;
import com.paycore.reconciliation.dto.DiscrepancyResolutionRequest;
import com.paycore.reconciliation.dto.ReconciliationRecordResponseDto;
import com.paycore.reconciliation.dto.ReconciliationRunRequest;
import com.paycore.reconciliation.dto.ReconciliationRunResponseDto;
import com.paycore.reconciliation.entity.*;
import com.paycore.reconciliation.repository.ReconciliationRecordRepository;
import com.paycore.reconciliation.repository.ReconciliationRunRepository;
import com.paycore.refund.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Reconciliation Service Unit Tests")
class ReconciliationServiceTest {

    @Mock
    private ReconciliationRunRepository runRepository;

    @Mock
    private ReconciliationRecordRepository recordRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private ProviderReconciliationAdapter providerAdapter;

    @Mock
    private ReconciliationMatcher reconciliationMatcher;

    @Mock
    private AuditLogService auditLogService;

    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(
                runRepository,
                recordRepository,
                paymentRepository,
                refundRepository,
                providerAdapter,
                reconciliationMatcher,
                auditLogService
        );
    }

    @Test
    @DisplayName("Should prevent concurrent reconciliation when a run is already active")
    void testConcurrencyGuardRejectsActiveRun() {
        when(runRepository.existsByStatus(RunStatus.RUNNING)).thenReturn(true);

        ReconciliationRunRequest request = new ReconciliationRunRequest();
        request.setRunType(RunType.FULL);

        assertThrows(DuplicateResourceException.class, () ->
                service.initiateReconciliationRun(request, "admin@paycore.com"));

        verify(runRepository, never()).save(any(ReconciliationRun.class));
    }

    @Test
    @DisplayName("Should execute reconciliation run, match records, and update run summary")
    void testSuccessfulReconciliationRun() {
        when(runRepository.existsByStatus(RunStatus.RUNNING)).thenReturn(false);

        ReconciliationRun savedInitialRun = new ReconciliationRun(
                "rec_run_test123", RunType.PAYMENTS, "admin_user",
                Instant.now().minusSeconds(3600), Instant.now()
        );
        savedInitialRun.setId(10L);
        savedInitialRun.setStatus(RunStatus.RUNNING);

        when(runRepository.save(any(ReconciliationRun.class))).thenReturn(savedInitialRun);

        Merchant merchant = new Merchant();
        merchant.setId(1L);

        Payment payment = new Payment();
        payment.setPaymentReference("PAY-101");
        payment.setProviderTransactionId("tx_101");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setMerchant(merchant);
        payment.setPaymentMethod(PaymentMethodType.CARD);
        payment.setStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findByDateRange(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(payment));

        ProviderTransactionRecord providerTx = new ProviderTransactionRecord(
                "tx_101", "PAY-101", 1L, new BigDecimal("100.00"), "USD", "SUCCESS", Instant.now(), "00", "Approved"
        );
        when(providerAdapter.fetchTransactionRecords(any(Instant.class), any(Instant.class), isNull()))
                .thenReturn(List.of(providerTx));

        ReconciliationRecord matchedRecord = new ReconciliationRecord(
                savedInitialRun, RecordType.PAYMENT, "PAY-101", "tx_101",
                new BigDecimal("100.00"), new BigDecimal("100.00"), "USD",
                "SUCCESS", "SUCCESS", DiscrepancyType.MATCHED, RecordStatus.MATCHED
        );

        when(reconciliationMatcher.matchPayments(any(ReconciliationRun.class), anyList(), anyList()))
                .thenReturn(List.of(matchedRecord));

        when(recordRepository.saveAll(anyList())).thenReturn(List.of(matchedRecord));

        ReconciliationRunRequest request = new ReconciliationRunRequest();
        request.setRunType(RunType.PAYMENTS);

        ReconciliationRunResponseDto response = service.initiateReconciliationRun(request, "admin_user");

        assertNotNull(response);
        assertEquals(RunStatus.COMPLETED, savedInitialRun.getStatus());
        assertEquals(1, savedInitialRun.getTotalRecords());
        assertEquals(1, savedInitialRun.getMatchedRecords());
        assertEquals(0, savedInitialRun.getDiscrepancyRecords());

        verify(recordRepository, atLeastOnce()).saveAll(anyList());
        verify(auditLogService, times(1)).logAction(
                eq("admin_user"), eq("ROLE_ADMIN"), isNull(),
                eq("RECONCILIATION_RUN_COMPLETED"), eq("RECONCILIATION"),
                anyString(), anyString(), isNull(), any(), eq("Reconciliation execution completed")
        );
    }

    @Test
    @DisplayName("Should resolve discrepancy, record notes, and create immutable audit log")
    void testResolveDiscrepancy() {
        ReconciliationRun run = new ReconciliationRun("run_test", RunType.PAYMENTS, "admin", Instant.now().minusSeconds(3600), Instant.now());
        ReconciliationRecord record = new ReconciliationRecord(
                run, RecordType.PAYMENT, "PAY-999", "tx_999",
                new BigDecimal("100.00"), new BigDecimal("90.00"), "USD",
                "SUCCESS", "SUCCESS", DiscrepancyType.AMOUNT_MISMATCH, RecordStatus.REVIEW_REQUIRED
        );
        record.setId(55L);

        when(recordRepository.findById(55L)).thenReturn(Optional.of(record));
        when(recordRepository.save(any(ReconciliationRecord.class))).thenAnswer(i -> i.getArgument(0));

        DiscrepancyResolutionRequest request = new DiscrepancyResolutionRequest();
        request.setNotes("Verified provider discount deduction with billing team.");
        request.setReason("FEE_ADJUSTMENT");

        ReconciliationRecordResponseDto resolved = service.resolveDiscrepancy(55L, request, "auditor_bob");

        assertNotNull(resolved);
        assertEquals(RecordStatus.RESOLVED, resolved.getStatus());
        assertEquals("auditor_bob", resolved.getResolvedBy());
        assertTrue(resolved.getResolutionNotes().contains("Verified provider discount"));

        verify(auditLogService, times(1)).logAction(
                eq("auditor_bob"), eq("ROLE_ADMIN"), isNull(),
                eq("DISCREPANCY_RESOLVED"), eq("RECONCILIATION_RECORD"),
                eq("55"), contains("Manual discrepancy resolution"),
                any(), any(), eq("Verified provider discount deduction with billing team.")
        );
    }

    @Test
    @DisplayName("Should reject resolving an already resolved discrepancy")
    void testRejectAlreadyResolved() {
        ReconciliationRun run = new ReconciliationRun("run_test", RunType.PAYMENTS, "admin", Instant.now().minusSeconds(3600), Instant.now());
        ReconciliationRecord record = new ReconciliationRecord(
                run, RecordType.PAYMENT, "PAY-999", "tx_999",
                new BigDecimal("100.00"), new BigDecimal("90.00"), "USD",
                "SUCCESS", "SUCCESS", DiscrepancyType.AMOUNT_MISMATCH, RecordStatus.RESOLVED
        );
        record.setId(55L);

        when(recordRepository.findById(55L)).thenReturn(Optional.of(record));

        DiscrepancyResolutionRequest request = new DiscrepancyResolutionRequest();
        request.setNotes("Try resolve again");
        request.setReason("DUPLICATE_ATTEMPT");

        assertThrows(IllegalStateException.class, () ->
                service.resolveDiscrepancy(55L, request, "auditor_bob"));

        verify(recordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject resolving a matched record that has no discrepancy")
    void testRejectMatchedRecordResolution() {
        ReconciliationRun run = new ReconciliationRun("run_test", RunType.PAYMENTS, "admin", Instant.now().minusSeconds(3600), Instant.now());
        ReconciliationRecord record = new ReconciliationRecord(
                run, RecordType.PAYMENT, "PAY-100", "tx_100",
                new BigDecimal("100.00"), new BigDecimal("100.00"), "USD",
                "SUCCESS", "SUCCESS", DiscrepancyType.MATCHED, RecordStatus.MATCHED
        );
        record.setId(60L);

        when(recordRepository.findById(60L)).thenReturn(Optional.of(record));

        DiscrepancyResolutionRequest request = new DiscrepancyResolutionRequest();
        request.setNotes("Not needed");
        request.setReason("NONE");

        assertThrows(IllegalArgumentException.class, () ->
                service.resolveDiscrepancy(60L, request, "auditor_bob"));
    }
}
