package com.paycore.dashboard.service;

import com.paycore.dashboard.dto.*;
import com.paycore.payment.entity.PaymentStatus;
import com.paycore.payment.repository.PaymentRepository;
import com.paycore.reconciliation.entity.DiscrepancyType;
import com.paycore.reconciliation.entity.ReconciliationRun;
import com.paycore.reconciliation.entity.RunStatus;
import com.paycore.reconciliation.entity.RunType;
import com.paycore.reconciliation.repository.ReconciliationRecordRepository;
import com.paycore.reconciliation.repository.ReconciliationRunRepository;
import com.paycore.refund.entity.RefundStatus;
import com.paycore.refund.repository.RefundRepository;
import com.paycore.webhook.repository.WebhookDeliveryAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Admin Dashboard Service Unit Tests")
class AdminDashboardServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private WebhookDeliveryAttemptRepository deliveryAttemptRepository;

    @Mock
    private ReconciliationRunRepository runRepository;

    @Mock
    private ReconciliationRecordRepository recordRepository;

    private AdminDashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new AdminDashboardService(
                paymentRepository,
                refundRepository,
                deliveryAttemptRepository,
                runRepository,
                recordRepository
        );
    }

    @Test
    @DisplayName("Should aggregate real dashboard KPI metrics correctly")
    void testGetDashboardSummary() {
        Instant start = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant end = Instant.now();

        when(paymentRepository.countPaymentsBetween(start, end)).thenReturn(100L);
        when(paymentRepository.countByStatusBetween(PaymentStatus.SUCCESS, start, end)).thenReturn(90L);
        when(paymentRepository.countByStatusBetween(PaymentStatus.FAILED, start, end)).thenReturn(8L);
        when(paymentRepository.countByStatusInBetween(anyList(), eq(start), eq(end))).thenReturn(1L);
        when(paymentRepository.countByStatusBetween(PaymentStatus.UNKNOWN, start, end)).thenReturn(1L);
        when(paymentRepository.sumSuccessfulAmountBetween(start, end)).thenReturn(new BigDecimal("12500.50"));

        when(refundRepository.countRefundsBetween(start, end)).thenReturn(5L);
        when(refundRepository.countByStatusBetween(RefundStatus.SUCCESS, start, end)).thenReturn(4L);
        when(refundRepository.sumSuccessfulAmountBetween(start, end)).thenReturn(new BigDecimal("350.00"));
        when(refundRepository.countByStatusInBetween(anyList(), eq(start), eq(end))).thenReturn(1L);

        when(deliveryAttemptRepository.countFailedDeliveries()).thenReturn(2L);
        when(deliveryAttemptRepository.countRetryingDeliveries()).thenReturn(1L);

        ReconciliationRun latestRun = new ReconciliationRun("run_kpi", RunType.FULL, "admin", start, end);
        latestRun.setStatus(RunStatus.COMPLETED);
        when(runRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.of(latestRun));

        when(recordRepository.countByStatusIn(anyList())).thenReturn(3L);

        AdminDashboardSummaryDto summary = dashboardService.getDashboardSummary(start, end);

        assertNotNull(summary);
        assertEquals(100L, summary.getTotalPayments());
        assertEquals(90L, summary.getSuccessfulPayments());
        assertEquals(8L, summary.getFailedPayments());
        assertEquals(new BigDecimal("12500.50"), summary.getTotalPaymentVolume());

        assertEquals(5L, summary.getTotalRefunds());
        assertEquals(4L, summary.getSuccessfulRefunds());
        assertEquals(new BigDecimal("350.00"), summary.getSuccessfulRefundVolume());

        assertEquals(2L, summary.getWebhookDeliveryFailures());
        assertEquals(1L, summary.getPendingWebhookRetries());
        assertEquals("COMPLETED", summary.getLatestReconciliationStatus());
        assertEquals(3L, summary.getUnresolvedDiscrepanciesCount());
    }

    @Test
    @DisplayName("Should calculate payment status distribution counts and percentages")
    void testGetPaymentStatusDistribution() {
        Instant start = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant end = Instant.now();

        List<Object[]> queryResults = List.of(
                new Object[]{PaymentStatus.SUCCESS, 80L},
                new Object[]{PaymentStatus.FAILED, 15L},
                new Object[]{PaymentStatus.PROCESSING, 5L}
        );

        when(paymentRepository.countGroupedByStatusBetween(start, end)).thenReturn(queryResults);

        List<StatusDistributionDto> distribution = dashboardService.getPaymentStatusDistribution(start, end);

        assertNotNull(distribution);
        assertEquals(3, distribution.size());

        StatusDistributionDto successDist = distribution.stream()
                .filter(d -> d.getStatus().equals("SUCCESS"))
                .findFirst().orElseThrow();
        assertEquals(80L, successDist.getCount());
        assertEquals(80.0, successDist.getPercentage());

        StatusDistributionDto failDist = distribution.stream()
                .filter(d -> d.getStatus().equals("FAILED"))
                .findFirst().orElseThrow();
        assertEquals(15L, failDist.getCount());
        assertEquals(15.0, failDist.getPercentage());
    }

    @Test
    @DisplayName("Should calculate reconciliation summary metrics")
    void testGetReconciliationSummary() {
        when(runRepository.count()).thenReturn(12L);
        when(recordRepository.countByStatusIn(anyList())).thenReturn(5L);

        ReconciliationRun latestRun = new ReconciliationRun("run_latest", RunType.FULL, "admin", Instant.now().minusSeconds(3600), Instant.now());
        latestRun.setStatus(RunStatus.COMPLETED);
        when(runRepository.findTop1ByOrderByCreatedAtDesc()).thenReturn(Optional.of(latestRun));

        List<Object[]> discrepancyTypeResults = List.of(
                new Object[]{DiscrepancyType.AMOUNT_MISMATCH, 2L},
                new Object[]{DiscrepancyType.MISSING_INTERNAL, 3L}
        );
        when(recordRepository.countGroupedByDiscrepancyType()).thenReturn(discrepancyTypeResults);

        ReconciliationSummaryDto summary = dashboardService.getReconciliationSummary();

        assertNotNull(summary);
        assertEquals(12L, summary.getTotalRuns());
        assertEquals(5L, summary.getUnresolvedDiscrepancies());
        assertEquals("run_latest", summary.getLatestRunReference());
        assertEquals("COMPLETED", summary.getLatestRunStatus());
        assertEquals(2L, summary.getDiscrepancyTypeCounts().get("AMOUNT_MISMATCH"));
        assertEquals(3L, summary.getDiscrepancyTypeCounts().get("MISSING_INTERNAL"));
    }
}
