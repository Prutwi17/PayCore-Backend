package com.paycore.payment.provider;

import java.math.BigDecimal;

/**
 * Standardized refund request payload passed to downstream payment providers.
 */
public class PaymentProviderRefundRequest {

    private final String refundReference;
    private final String paymentReference;
    private final String providerTransactionId;
    private final BigDecimal amount;
    private final String currency;
    private final String reason;
    private final String simulationScenario;

    public PaymentProviderRefundRequest(
            String refundReference,
            String paymentReference,
            String providerTransactionId,
            BigDecimal amount,
            String currency,
            String reason,
            String simulationScenario
    ) {
        this.refundReference = refundReference;
        this.paymentReference = paymentReference;
        this.providerTransactionId = providerTransactionId;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
        this.simulationScenario = simulationScenario;
    }

    public String getRefundReference() {
        return refundReference;
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

    public String getSimulationScenario() {
        return simulationScenario;
    }
}
