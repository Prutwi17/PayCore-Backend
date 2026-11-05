package com.paycore.refund.dto;

import java.math.BigDecimal;

public class RefundReservationResult {

    private final Long refundId;
    private final String refundReference;
    private final Long paymentId;
    private final String paymentReference;
    private final String providerTransactionId;
    private final BigDecimal amount;
    private final String currency;
    private final String reason;

    public RefundReservationResult(
            Long refundId,
            String refundReference,
            Long paymentId,
            String paymentReference,
            String providerTransactionId,
            BigDecimal amount,
            String currency,
            String reason
    ) {
        this.refundId = refundId;
        this.refundReference = refundReference;
        this.paymentId = paymentId;
        this.paymentReference = paymentReference;
        this.providerTransactionId = providerTransactionId;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
    }

    public Long getRefundId() {
        return refundId;
    }

    public String getRefundReference() {
        return refundReference;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReason() {
        return reason;
    }
}
