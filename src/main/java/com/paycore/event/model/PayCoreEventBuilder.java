package com.paycore.event.model;

import com.paycore.payment.entity.Payment;
import com.paycore.refund.entity.Refund;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sanitized domain event builder ensuring zero sensitive information leakage.
 */
public final class PayCoreEventBuilder {

    private PayCoreEventBuilder() {
    }

    public static PayCoreDomainEvent fromPayment(Payment payment, EventType eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentReference", payment.getPaymentReference());
        payload.put("merchantId", payment.getMerchant().getId());
        payload.put("merchantCode", payment.getMerchant().getMerchantCode());
        payload.put("amount", payment.getAmount() != null ? payment.getAmount().toPlainString() : "0.00");
        payload.put("currency", payment.getCurrency());
        payload.put("status", payment.getStatus().name());
        payload.put("paymentMethod", payment.getPaymentMethod().name());
        payload.put("customerReference", payment.getCustomerReference());
        payload.put("description", payment.getDescription());
        payload.put("provider", payment.getProvider());
        payload.put("providerTransactionId", payment.getProviderTransactionId());
        payload.put("failureCode", payment.getFailureCode());
        payload.put("failureMessage", payment.getFailureMessage());
        payload.put("createdAt", payment.getCreatedAt() != null ? payment.getCreatedAt().toString() : null);
        payload.put("processedAt", payment.getProcessedAt() != null ? payment.getProcessedAt().toString() : null);

        return PayCoreDomainEvent.builder()
                .eventId("evt_" + UUID.randomUUID().toString().replace("-", ""))
                .eventType(eventType)
                .aggregateType("PAYMENT")
                .aggregateId(payment.getPaymentReference())
                .merchantId(payment.getMerchant().getId())
                .occurredAt(Instant.now())
                .eventVersion(1)
                .payload(payload)
                .build();
    }

    public static PayCoreDomainEvent fromRefund(Refund refund, EventType eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("refundReference", refund.getRefundReference());
        payload.put("paymentReference", refund.getPayment().getPaymentReference());
        payload.put("merchantId", refund.getMerchant().getId());
        payload.put("merchantCode", refund.getMerchant().getMerchantCode());
        payload.put("amount", refund.getAmount() != null ? refund.getAmount().toPlainString() : "0.00");
        payload.put("currency", refund.getCurrency());
        payload.put("status", refund.getStatus().name());
        payload.put("reason", refund.getReason());
        payload.put("providerRefundId", refund.getProviderRefundId());
        payload.put("failureCode", refund.getFailureCode());
        payload.put("failureMessage", refund.getFailureMessage());
        payload.put("requestedAt", refund.getRequestedAt() != null ? refund.getRequestedAt().toString() : null);
        payload.put("processedAt", refund.getProcessedAt() != null ? refund.getProcessedAt().toString() : null);

        return PayCoreDomainEvent.builder()
                .eventId("evt_" + UUID.randomUUID().toString().replace("-", ""))
                .eventType(eventType)
                .aggregateType("REFUND")
                .aggregateId(refund.getRefundReference())
                .merchantId(refund.getMerchant().getId())
                .occurredAt(Instant.now())
                .eventVersion(1)
                .payload(payload)
                .build();
    }
}
