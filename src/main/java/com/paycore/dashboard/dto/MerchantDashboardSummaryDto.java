package com.paycore.dashboard.dto;

import com.paycore.transaction.dto.TransactionItemDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MerchantDashboardSummaryDto {

    private long totalPayments;
    private long successfulPayments;
    private long failedPayments;
    private long pendingPayments;
    private BigDecimal totalPaymentVolume = BigDecimal.ZERO;

    private long totalRefunds;
    private long successfulRefunds;
    private BigDecimal totalRefundVolume = BigDecimal.ZERO;
    private BigDecimal netVolume = BigDecimal.ZERO;

    private long totalWebhooks;
    private long failedWebhooks;

    private List<TransactionItemDto> recentTransactions = new ArrayList<>();

    public MerchantDashboardSummaryDto() {
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

    public long getPendingPayments() {
        return pendingPayments;
    }

    public void setPendingPayments(long pendingPayments) {
        this.pendingPayments = pendingPayments;
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

    public BigDecimal getTotalRefundVolume() {
        return totalRefundVolume;
    }

    public void setTotalRefundVolume(BigDecimal totalRefundVolume) {
        this.totalRefundVolume = totalRefundVolume;
    }

    public BigDecimal getNetVolume() {
        return netVolume;
    }

    public void setNetVolume(BigDecimal netVolume) {
        this.netVolume = netVolume;
    }

    public long getTotalWebhooks() {
        return totalWebhooks;
    }

    public void setTotalWebhooks(long totalWebhooks) {
        this.totalWebhooks = totalWebhooks;
    }

    public long getFailedWebhooks() {
        return failedWebhooks;
    }

    public void setFailedWebhooks(long failedWebhooks) {
        this.failedWebhooks = failedWebhooks;
    }

    public List<TransactionItemDto> getRecentTransactions() {
        return recentTransactions;
    }

    public void setRecentTransactions(List<TransactionItemDto> recentTransactions) {
        this.recentTransactions = recentTransactions;
    }
}
