package com.paycore.payment.provider;

import com.paycore.payment.entity.PaymentMethodType;
import com.paycore.payment.exception.PaymentProviderTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SimulatedPaymentProviderTest {

    private SimulatedPaymentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SimulatedPaymentProvider();
    }

    @Test
    @DisplayName("Default scenario yields SUCCESS outcome")
    void testDefaultSuccessfulPayment() {
        PaymentProviderRequest request = new PaymentProviderRequest(
                "pay_test123", "att_req_1", new BigDecimal("100.00"), "INR",
                PaymentMethodType.CARD, "cust_1", "Regular checkout", null
        );

        PaymentProviderResponse response = provider.initiatePayment(request);

        assertNotNull(response);
        assertEquals(PaymentProviderStatus.SUCCESS, response.getStatus());
        assertNotNull(response.getProviderTransactionId());
        assertTrue(response.getProviderTransactionId().startsWith("sim_tx_"));
        assertEquals("200_APPROVED", response.getResponseCode());
    }

    @Test
    @DisplayName("Simulation scenario FAIL returns FAILED outcome")
    void testFailedPaymentScenario() {
        PaymentProviderRequest request = new PaymentProviderRequest(
                "pay_test_fail", "att_req_2", new BigDecimal("500.00"), "USD",
                PaymentMethodType.CARD, "cust_2", "Checkout order", "FAIL"
        );

        PaymentProviderResponse response = provider.initiatePayment(request);

        assertNotNull(response);
        assertEquals(PaymentProviderStatus.FAILED, response.getStatus());
        assertEquals("DECLINED_INSUFFICIENT_FUNDS", response.getResponseCode());
    }

    @Test
    @DisplayName("Simulation scenario REQUIRES_ACTION returns 3DS redirect")
    void testRequiresActionScenario() {
        PaymentProviderRequest request = new PaymentProviderRequest(
                "pay_test_3ds", "att_req_3", new BigDecimal("250.00"), "EUR",
                PaymentMethodType.CARD, "cust_3", "3DS Order", "REQUIRES_ACTION"
        );

        PaymentProviderResponse response = provider.initiatePayment(request);

        assertNotNull(response);
        assertEquals(PaymentProviderStatus.REQUIRES_ACTION, response.getStatus());
        assertNotNull(response.getActionUrl());
        assertTrue(response.getActionUrl().contains("3ds/verify"));
    }

    @Test
    @DisplayName("Simulation scenario TIMEOUT throws PaymentProviderTimeoutException")
    void testTimeoutScenarioThrowsException() {
        PaymentProviderRequest request = new PaymentProviderRequest(
                "pay_test_timeout", "att_req_4", new BigDecimal("1000.00"), "INR",
                PaymentMethodType.CARD, "cust_4", "Order [SIM_TIMEOUT]", null
        );

        assertThrows(PaymentProviderTimeoutException.class, () -> provider.initiatePayment(request));
    }

    @Test
    @DisplayName("Status query confirms transaction")
    void testQueryPaymentStatus() {
        PaymentProviderResponse response = provider.queryPaymentStatus("sim_tx_123456", "pay_ref");
        assertEquals(PaymentProviderStatus.SUCCESS, response.getStatus());
        assertEquals("200_CONFIRMED", response.getResponseCode());
    }

    @Test
    @DisplayName("Cancellation voids transaction")
    void testCancelPayment() {
        PaymentProviderResponse response = provider.cancelPayment("sim_tx_999", "pay_ref");
        assertEquals(PaymentProviderStatus.CANCELLED, response.getStatus());
    }
}
