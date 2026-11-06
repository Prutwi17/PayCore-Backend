package com.paycore.dashboard.service;

import com.paycore.dashboard.dto.MerchantDashboardSummaryDto;
import com.paycore.payment.entity.PaymentStatus;
import com.paycore.payment.repository.PaymentRepository;
import com.paycore.refund.entity.RefundStatus;
import com.paycore.refund.repository.RefundRepository;
import com.paycore.transaction.dto.TransactionItemDto;
import com.paycore.transaction.service.TransactionHistoryService;
import com.paycore.webhook.repository.WebhookDeliveryAttemptRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MerchantDashboardService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final WebhookDeliveryAttemptRepository webhookRepository;
    private final TransactionHistoryService transactionHistoryService;

    public MerchantDashboardService(
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            WebhookDeliveryAttemptRepository webhookRepository,
            TransactionHistoryService transactionHistoryService
    ) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.webhookRepository = webhookRepository;
        this.transactionHistoryService = transactionHistoryService;
    }

    public MerchantDashboardSummaryDto getMerchantSummary(Long merchantId) {
        MerchantDashboardSummaryDto summary = new MerchantDashboardSummaryDto();

        if (merchantId == null) {
            return summary;
        }

        // 1. Payment Metrics
        long totalPayments = paymentRepository.countByMerchantId(merchantId);
        long successPayments = paymentRepository.countByMerchantIdAndStatus(merchantId, PaymentStatus.SUCCESS);
        long failedPayments = paymentRepository.countByMerchantIdAndStatus(merchantId, PaymentStatus.FAILED);
        long pendingPayments = paymentRepository.countByMerchantIdAndStatusIn(
                merchantId, List.of(PaymentStatus.CREATED, PaymentStatus.PROCESSING, PaymentStatus.REQUIRES_ACTION)
        );
        BigDecimal paymentVolume = paymentRepository.sumSuccessfulAmountByMerchantId(merchantId);
        if (paymentVolume == null) {
            paymentVolume = BigDecimal.ZERO;
        }

        summary.setTotalPayments(totalPayments);
        summary.setSuccessfulPayments(successPayments);
        summary.setFailedPayments(failedPayments);
        summary.setPendingPayments(pendingPayments);
        summary.setTotalPaymentVolume(paymentVolume);

        // 2. Refund Metrics
        long totalRefunds = refundRepository.countByMerchantId(merchantId);
        long successRefunds = refundRepository.countByMerchantIdAndStatus(merchantId, RefundStatus.SUCCESS);
        BigDecimal refundVolume = refundRepository.sumSuccessfulAmountByMerchantId(merchantId);
        if (refundVolume == null) {
            refundVolume = BigDecimal.ZERO;
        }

        summary.setTotalRefunds(totalRefunds);
        summary.setSuccessfulRefunds(successRefunds);
        summary.setTotalRefundVolume(refundVolume);
        summary.setNetVolume(paymentVolume.subtract(refundVolume));

        // 3. Webhook Metrics
        long totalWebhooks = webhookRepository.countByMerchantId(merchantId);
        long failedWebhooks = webhookRepository.countFailedByMerchantId(merchantId);
        summary.setTotalWebhooks(totalWebhooks);
        summary.setFailedWebhooks(failedWebhooks);

        // 4. Recent Transactions (Latest 5 items)
        try {
            Page<TransactionItemDto> recentPage = transactionHistoryService.searchMerchantTransactions(
                    merchantId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"))
            );
            summary.setRecentTransactions(recentPage.getContent());
        } catch (Exception ignored) {
            // If no transactions yet, default to empty list
        }

        return summary;
    }
}
