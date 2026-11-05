package com.paycore.payment.provider;

/**
 * Pluggable payment provider abstraction interface.
 * Defines the contract that concrete provider integrations (e.g. Simulated, Stripe, etc.) must implement.
 */
public interface PaymentProvider {

    /**
     * Unique identifier code for this provider (e.g. "SIMULATED").
     */
    String getProviderName();

    /**
     * Initiates a payment attempt with the downstream provider.
     */
    PaymentProviderResponse initiatePayment(PaymentProviderRequest request);

    /**
     * Queries the provider for the authoritative status of a transaction.
     */
    PaymentProviderResponse queryPaymentStatus(String providerTransactionId, String requestReference);

    /**
     * Attempts cancellation of an in-flight or authorized payment at the provider.
     */
    PaymentProviderResponse cancelPayment(String providerTransactionId, String requestReference);

    /**
     * Initiates a refund attempt with the downstream provider.
     */
    PaymentProviderRefundResponse initiateRefund(PaymentProviderRefundRequest request);

    /**
     * Queries the provider for the authoritative status of a refund transaction.
     */
    PaymentProviderRefundResponse queryRefundStatus(String providerRefundId, String refundReference);
}
