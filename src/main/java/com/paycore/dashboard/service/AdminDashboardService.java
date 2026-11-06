package com.paycore.dashboard.service;

import com.paycore.dashboard.dto.*;
import com.paycore.payment.entity.Payment;
import com.paycore.payment.entity.PaymentStatus;
import com.paycore.payment.repository.PaymentRepository;
import com.paycore.reconciliation.entity.DiscrepancyType;
import com.paycore.reconciliation.entity.ReconciliationRun;
import com.paycore.reconciliation.entity.RecordStatus;
import com.paycore.reconciliation.repository.ReconciliationRecordRepository;
import com.paycore.reconciliation.repository.ReconciliationRunRepository;
import com.paycore.refund.entity.Refund;
import com.paycore.refund.entity.RefundStatus;
import com.paycore.refund.repository.RefundRepository;
import com.paycore.webhook.repository.WebhookDeliveryAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final WebhookDeliveryAttemptRepository deliveryAttemptRepository;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationRecordRepository recordRepository;

    public AdminDashboardService(
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            WebhookDeliveryAttemptRepository deliveryAttemptRepository,
            ReconciliationRunRepository runRepository,
            ReconciliationRecordRepository recordRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.runRepository = runRepository;
        this.recordRepository = recordRepository;
    }

    public AdminDashboardSummaryDto getDashboardSummary(Instant startDate, Instant endDate) {
        AdminDashboardSummaryDto summary = new AdminDashboardSummaryDto();

        summary.setTotalPayments(paymentRepository.countPaymentsBetween(startDate, endDate));
        summary.setSuccessfulPayments(paymentRepository.countByStatusBetween(PaymentStatus.SUCCESS, startDate, endDate));
        summary.setFailedPayments(paymentRepository.countByStatusBetween(PaymentStatus.FAILED, startDate, endDate));
        summary.setPendingProcessingPayments(paymentRepository.countByStatusInBetween(
                List.of(PaymentStatus.CREATED, PaymentStatus.PROCESSING), startDate, endDate));
        summary.setUnknownPayments(paymentRepository.countByStatusBetween(PaymentStatus.UNKNOWN, startDate, endDate));
        summary.setTotalPaymentVolume(paymentRepository.sumSuccessfulAmountBetween(startDate, endDate));

        summary.setTotalRefunds(refundRepository.countRefundsBetween(startDate, endDate));
        summary.setSuccessfulRefunds(refundRepository.countByStatusBetween(RefundStatus.SUCCESS, startDate, endDate));
        summary.setSuccessfulRefundVolume(refundRepository.sumSuccessfulAmountBetween(startDate, endDate));
        summary.setPendingRefunds(refundRepository.countByStatusInBetween(
                List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING), startDate, endDate));

        summary.setWebhookDeliveryFailures(deliveryAttemptRepository.countFailedDeliveries());
        summary.setPendingWebhookRetries(deliveryAttemptRepository.countRetryingDeliveries());

        Optional<ReconciliationRun> latestRun = runRepository.findTop1ByOrderByCreatedAtDesc();
        if (latestRun.isPresent()) {
            summary.setLatestReconciliationStatus(latestRun.get().getStatus().name());
            summary.setLatestReconciliationAt(latestRun.get().getCompletedAt() != null
                    ? latestRun.get().getCompletedAt()
                    : latestRun.get().getCreatedAt());
        } else {
            summary.setLatestReconciliationStatus("NEVER_RUN");
        }

        summary.setUnresolvedDiscrepanciesCount(
                recordRepository.countByStatusIn(List.of(RecordStatus.UNMATCHED, RecordStatus.REVIEW_REQUIRED)));

        return summary;
    }

    public List<PaymentTrendPointDto> getPaymentTrends(Instant startDate, Instant endDate) {
        List<Payment> payments = paymentRepository.findByDateRange(startDate, endDate);
        if (payments.isEmpty()) {
            return Collections.emptyList();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, List<Payment>> byDate = new TreeMap<>();

        for (Payment p : payments) {
            String dateKey = p.getCreatedAt().atZone(ZoneOffset.UTC).format(formatter);
            byDate.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(p);
        }

        List<PaymentTrendPointDto> points = new ArrayList<>();
        for (Map.Entry<String, List<Payment>> entry : byDate.entrySet()) {
            String date = entry.getKey();
            List<Payment> list = entry.getValue();

            long total = list.size();
            long success = list.stream().filter(p -> p.getStatus() == PaymentStatus.SUCCESS).count();
            long failed = list.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).count();
            BigDecimal amount = list.stream()
                    .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            points.add(new PaymentTrendPointDto(date, total, success, failed, amount));
        }

        return points;
    }

    public List<RefundTrendPointDto> getRefundTrends(Instant startDate, Instant endDate) {
        List<Refund> refunds = refundRepository.findByDateRange(startDate, endDate);
        if (refunds.isEmpty()) {
            return Collections.emptyList();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, List<Refund>> byDate = new TreeMap<>();

        for (Refund r : refunds) {
            String dateKey = r.getCreatedAt().atZone(ZoneOffset.UTC).format(formatter);
            byDate.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(r);
        }

        List<RefundTrendPointDto> points = new ArrayList<>();
        for (Map.Entry<String, List<Refund>> entry : byDate.entrySet()) {
            String date = entry.getKey();
            List<Refund> list = entry.getValue();

            long total = list.size();
            long success = list.stream().filter(r -> r.getStatus() == RefundStatus.SUCCESS).count();
            BigDecimal amount = list.stream()
                    .filter(r -> r.getStatus() == RefundStatus.SUCCESS)
                    .map(Refund::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            points.add(new RefundTrendPointDto(date, total, success, amount));
        }

        return points;
    }

    public List<StatusDistributionDto> getPaymentStatusDistribution(Instant startDate, Instant endDate) {
        List<Object[]> grouped = paymentRepository.countGroupedByStatusBetween(startDate, endDate);
        long total = grouped.stream().mapToLong(row -> (Long) row[1]).sum();

        List<StatusDistributionDto> distributions = new ArrayList<>();
        for (Object[] row : grouped) {
            PaymentStatus status = (PaymentStatus) row[0];
            long count = (Long) row[1];
            double percentage = total > 0
                    ? BigDecimal.valueOf((double) count * 100 / total).setScale(1, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;
            distributions.add(new StatusDistributionDto(status.name(), count, percentage));
        }

        return distributions;
    }

    public ReconciliationSummaryDto getReconciliationSummary() {
        ReconciliationSummaryDto summary = new ReconciliationSummaryDto();
        summary.setTotalRuns(runRepository.count());
        summary.setUnresolvedDiscrepancies(
                recordRepository.countByStatusIn(List.of(RecordStatus.UNMATCHED, RecordStatus.REVIEW_REQUIRED)));

        Optional<ReconciliationRun> latest = runRepository.findTop1ByOrderByCreatedAtDesc();
        if (latest.isPresent()) {
            summary.setLatestRunReference(latest.get().getRunReference());
            summary.setLatestRunStatus(latest.get().getStatus().name());
            summary.setLatestRunCompletedAt(latest.get().getCompletedAt());
        }

        Map<String, Long> typeCounts = new HashMap<>();
        List<Object[]> grouped = recordRepository.countGroupedByDiscrepancyType();
        for (Object[] row : grouped) {
            DiscrepancyType type = (DiscrepancyType) row[0];
            Long count = (Long) row[1];
            typeCounts.put(type.name(), count);
        }
        summary.setDiscrepancyTypeCounts(typeCounts);

        return summary;
    }
}
