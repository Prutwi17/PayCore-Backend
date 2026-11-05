package com.paycore.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Calculated refundable balance and eligibility for a payment")
public class RefundableAmountDto {

    private String paymentReference;
    private String currency;
    private BigDecimal originalAmount;
    private BigDecimal totalRefundedAmount;
    private BigDecimal pendingRefundAmount;
    private BigDecimal remainingRefundableAmount;
    private boolean refundable;

    public RefundableAmountDto() {
    }

    public RefundableAmountDto(
            String paymentReference,
            String currency,
            BigDecimal originalAmount,
            BigDecimal totalRefundedAmount,
            BigDecimal pendingRefundAmount,
            BigDecimal remainingRefundableAmount,
            boolean refundable
    ) {
        this.paymentReference = paymentReference;
        this.currency = currency;
        this.originalAmount = originalAmount;
        this.totalRefundedAmount = totalRefundedAmount;
        this.pendingRefundAmount = pendingRefundAmount;
        this.remainingRefundableAmount = remainingRefundableAmount;
        this.refundable = refundable;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    public BigDecimal getTotalRefundedAmount() {
        return totalRefundedAmount;
    }

    public void setTotalRefundedAmount(BigDecimal totalRefundedAmount) {
        this.totalRefundedAmount = totalRefundedAmount;
    }

    public BigDecimal getPendingRefundAmount() {
        return pendingRefundAmount;
    }

    public void setPendingRefundAmount(BigDecimal pendingRefundAmount) {
        this.pendingRefundAmount = pendingRefundAmount;
    }

    public BigDecimal getRemainingRefundableAmount() {
        return remainingRefundableAmount;
    }

    public void setRemainingRefundableAmount(BigDecimal remainingRefundableAmount) {
        this.remainingRefundableAmount = remainingRefundableAmount;
    }

    public boolean isRefundable() {
        return refundable;
    }

    public void setRefundable(boolean refundable) {
        this.refundable = refundable;
    }
}
