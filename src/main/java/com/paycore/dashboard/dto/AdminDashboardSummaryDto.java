package com.paycore.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class AdminDashboardSummaryDto {

    private long totalPayments;
    private long successfulPayments;
    private long failedPayments;
    private long pendingProcessingPayments;
    private long unknownPayments;
    private BigDecimal totalPaymentVolume;

    private long totalRefunds;
    private long successfulRefunds;
    private BigDecimal successfulRefundVolume;
    private long pendingRefunds;

    private long webhookDeliveryFailures;
    private long pendingWebhookRetries;

    private String latestReconciliationStatus;
    private Instant latestReconciliationAt;
    private long unresolvedDiscrepanciesCount;

    public AdminDashboardSummaryDto() {
    }

    public long getTotalPayments() {
        return totalPayments;
    }

    public void setTotalPayments(long totalPayments) {
        this.totalPayments = totalPayments;
    }

    public long getSuccessfulPayments() {
        return successfulPayments;
    }

    public void setSuccessfulPayments(long successfulPayments) {
        this.successfulPayments = successfulPayments;
    }

    public long getFailedPayments() {
        return failedPayments;
    }

    public void setFailedPayments(long failedPayments) {
        this.failedPayments = failedPayments;
    }

    public long getPendingProcessingPayments() {
        return pendingProcessingPayments;
    }

    public void setPendingProcessingPayments(long pendingProcessingPayments) {
        this.pendingProcessingPayments = pendingProcessingPayments;
    }

    public long getUnknownPayments() {
        return unknownPayments;
    }

    public void setUnknownPayments(long unknownPayments) {
        this.unknownPayments = unknownPayments;
    }

    public BigDecimal getTotalPaymentVolume() {
        return totalPaymentVolume;
    }

    public void setTotalPaymentVolume(BigDecimal totalPaymentVolume) {
        this.totalPaymentVolume = totalPaymentVolume;
    }

    public long getTotalRefunds() {
        return totalRefunds;
    }

    public void setTotalRefunds(long totalRefunds) {
        this.totalRefunds = totalRefunds;
    }

    public long getSuccessfulRefunds() {
        return successfulRefunds;
    }

    public void setSuccessfulRefunds(long successfulRefunds) {
        this.successfulRefunds = successfulRefunds;
    }

    public BigDecimal getSuccessfulRefundVolume() {
        return successfulRefundVolume;
    }

    public void setSuccessfulRefundVolume(BigDecimal successfulRefundVolume) {
        this.successfulRefundVolume = successfulRefundVolume;
    }

    public long getPendingRefunds() {
        return pendingRefunds;
    }

    public void setPendingRefunds(long pendingRefunds) {
        this.pendingRefunds = pendingRefunds;
    }

    public long getWebhookDeliveryFailures() {
        return webhookDeliveryFailures;
    }

    public void setWebhookDeliveryFailures(long webhookDeliveryFailures) {
        this.webhookDeliveryFailures = webhookDeliveryFailures;
    }

    public long getPendingWebhookRetries() {
        return pendingWebhookRetries;
    }

    public void setPendingWebhookRetries(long pendingWebhookRetries) {
        this.pendingWebhookRetries = pendingWebhookRetries;
    }

    public String getLatestReconciliationStatus() {
        return latestReconciliationStatus;
    }

    public void setLatestReconciliationStatus(String latestReconciliationStatus) {
        this.latestReconciliationStatus = latestReconciliationStatus;
    }

    public Instant getLatestReconciliationAt() {
        return latestReconciliationAt;
    }

    public void setLatestReconciliationAt(Instant latestReconciliationAt) {
        this.latestReconciliationAt = latestReconciliationAt;
    }

    public long getUnresolvedDiscrepanciesCount() {
        return unresolvedDiscrepanciesCount;
    }

    public void setUnresolvedDiscrepanciesCount(long unresolvedDiscrepanciesCount) {
        this.unresolvedDiscrepanciesCount = unresolvedDiscrepanciesCount;
    }
}
