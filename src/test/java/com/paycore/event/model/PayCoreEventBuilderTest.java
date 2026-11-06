package com.paycore.event.model;

import com.paycore.merchant.entity.Merchant;
import com.paycore.payment.entity.Payment;
import com.paycore.payment.entity.PaymentMethodType;
import com.paycore.payment.entity.PaymentStatus;
import com.paycore.refund.entity.Refund;
import com.paycore.refund.entity.RefundStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain Event Builder Unit Tests")
class PayCoreEventBuilderTest {

    @Test
    @DisplayName("Converts Payment to sanitized PAYMENT_SUCCESS event")
    void testBuildPaymentEvent() {
        Merchant merchant = new Merchant();
        merchant.setId(10L);
        merchant.setMerchantCode("MCH_TEST_01");

        Payment payment = new Payment();
        payment.setId(100L);
        payment.setPaymentReference("pay_ref_100");
        payment.setMerchant(merchant);
        payment.setAmount(new BigDecimal("149.99"));
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaymentMethod(PaymentMethodType.CARD);
        payment.setCustomerReference("cust_john@test.com");
        payment.setDescription("Order #42");
        payment.setCreatedAt(Instant.now());
        payment.setProcessedAt(Instant.now());

        PayCoreDomainEvent event = PayCoreEventBuilder.fromPayment(payment, EventType.PAYMENT_SUCCESS);

        assertNotNull(event);
        assertTrue(event.getEventId().startsWith("evt_"));
        assertEquals(EventType.PAYMENT_SUCCESS, event.getEventType());
        assertEquals("PAYMENT", event.getAggregateType());
        assertEquals("pay_ref_100", event.getAggregateId());
        assertEquals(10L, event.getMerchantId());

        // Verify payload contents
        assertEquals("pay_ref_100", event.getPayload().get("paymentReference"));
        assertEquals("MCH_TEST_01", event.getPayload().get("merchantCode"));
        assertEquals("149.99", event.getPayload().get("amount"));
        assertEquals("INR", event.getPayload().get("currency"));
        assertEquals("SUCCESS", event.getPayload().get("status"));
        assertEquals("CARD", event.getPayload().get("paymentMethod"));

        // Ensure zero sensitive data
        assertFalse(event.getPayload().containsKey("cvv"));
        assertFalse(event.getPayload().containsKey("cardNumber"));
        assertFalse(event.getPayload().containsKey("password"));
    }

    @Test
    @DisplayName("Converts Refund to sanitized REFUND_SUCCESS event")
    void testBuildRefundEvent() {
        Merchant merchant = new Merchant();
        merchant.setId(10L);
        merchant.setMerchantCode("MCH_TEST_01");

        Payment payment = new Payment();
        payment.setPaymentReference("pay_ref_100");

        Refund refund = new Refund();
        refund.setId(200L);
        refund.setRefundReference("ref_200");
        refund.setPayment(payment);
        refund.setMerchant(merchant);
        refund.setAmount(new BigDecimal("50.00"));
        refund.setCurrency("INR");
        refund.setStatus(RefundStatus.SUCCESS);
        refund.setReason("Item returned");
        refund.setRequestedAt(Instant.now());
        refund.setProcessedAt(Instant.now());

        PayCoreDomainEvent event = PayCoreEventBuilder.fromRefund(refund, EventType.REFUND_SUCCESS);

        assertNotNull(event);
        assertTrue(event.getEventId().startsWith("evt_"));
        assertEquals(EventType.REFUND_SUCCESS, event.getEventType());
        assertEquals("REFUND", event.getAggregateType());
        assertEquals("ref_200", event.getAggregateId());
        assertEquals(10L, event.getMerchantId());
        assertEquals("50.00", event.getPayload().get("amount"));
        assertEquals("ref_200", event.getPayload().get("refundReference"));
        assertEquals("pay_ref_100", event.getPayload().get("paymentReference"));
    }
}
