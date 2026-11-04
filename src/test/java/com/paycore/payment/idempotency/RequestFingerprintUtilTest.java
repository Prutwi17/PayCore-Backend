package com.paycore.payment.idempotency.util;

import com.paycore.payment.dto.PaymentCreateRequest;
import com.paycore.payment.entity.PaymentMethodType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestFingerprintUtilTest {

    @Test
    @DisplayName("Identical payloads must produce identical fingerprints")
    void identicalPayloads_produceIdenticalFingerprints() {
        PaymentCreateRequest req1 = new PaymentCreateRequest(
                new BigDecimal("100.00"), "USD", "Test payment", PaymentMethodType.CARD, "cust_1"
        );
        PaymentCreateRequest req2 = new PaymentCreateRequest(
                new BigDecimal("100.00"), "usd", "Test payment", PaymentMethodType.CARD, "cust_1"
        );

        String fp1 = RequestFingerprintUtil.computeFingerprint(req1);
        String fp2 = RequestFingerprintUtil.computeFingerprint(req2);

        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).hasSize(64);
    }

    @Test
    @DisplayName("Different amounts must produce different fingerprints")
    void differentAmount_producesDifferentFingerprint() {
        PaymentCreateRequest req1 = new PaymentCreateRequest(
                new BigDecimal("100.00"), "USD", "Test payment", PaymentMethodType.CARD, "cust_1"
        );
        PaymentCreateRequest req2 = new PaymentCreateRequest(
                new BigDecimal("100.01"), "USD", "Test payment", PaymentMethodType.CARD, "cust_1"
        );

        String fp1 = RequestFingerprintUtil.computeFingerprint(req1);
        String fp2 = RequestFingerprintUtil.computeFingerprint(req2);

        assertThat(fp1).isNotEqualTo(fp2);
    }

    @Test
    @DisplayName("Different customer references must produce different fingerprints")
    void differentCustomer_producesDifferentFingerprint() {
        PaymentCreateRequest req1 = new PaymentCreateRequest(
                new BigDecimal("100.00"), "USD", "Test payment", PaymentMethodType.CARD, "cust_1"
        );
        PaymentCreateRequest req2 = new PaymentCreateRequest(
                new BigDecimal("100.00"), "USD", "Test payment", PaymentMethodType.CARD, "cust_2"
        );

        String fp1 = RequestFingerprintUtil.computeFingerprint(req1);
        String fp2 = RequestFingerprintUtil.computeFingerprint(req2);

        assertThat(fp1).isNotEqualTo(fp2);
    }

    @Test
    @DisplayName("Null request throws IllegalArgumentException")
    void nullRequest_throwsException() {
        assertThatThrownBy(() -> RequestFingerprintUtil.computeFingerprint(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
