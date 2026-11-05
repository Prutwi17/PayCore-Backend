package com.paycore.webhook.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Webhook Signer & Verification Unit Tests")
class WebhookSignerTest {

    private WebhookSigner webhookSigner;

    @BeforeEach
    void setUp() {
        webhookSigner = new WebhookSigner();
    }

    @Test
    @DisplayName("Generated secret starts with whsec_ and has 64 hex characters (32 bytes)")
    void testSecretGeneration() {
        String secret = webhookSigner.generateSecret();
        assertNotNull(secret);
        assertTrue(secret.startsWith("whsec_"));
        assertEquals(70, secret.length()); // "whsec_" (6) + 64 hex chars
    }

    @Test
    @DisplayName("Payload signing produces valid t=...,v1=... format")
    void testSignPayload() {
        String secret = webhookSigner.generateSecret();
        String payload = "{\"event\":\"payment.success\",\"amount\":100.00}";
        long timestamp = Instant.now().getEpochSecond();

        String signatureHeader = webhookSigner.signPayload(payload, timestamp, secret);
        assertNotNull(signatureHeader);
        assertTrue(signatureHeader.startsWith("t=" + timestamp + ",v1="));
    }

    @Test
    @DisplayName("Valid signature passes constant-time verification within tolerance window")
    void testVerifyValidSignature() {
        String secret = webhookSigner.generateSecret();
        String payload = "{\"event\":\"payment.success\",\"amount\":100.00}";
        long timestamp = Instant.now().getEpochSecond();

        String signatureHeader = webhookSigner.signPayload(payload, timestamp, secret);
        boolean isValid = webhookSigner.verifySignature(payload, signatureHeader, secret, 300L);

        assertTrue(isValid, "Expected valid signature to verify successfully");
    }

    @Test
    @DisplayName("Signature verification fails if payload was tampered")
    void testTamperedPayloadFails() {
        String secret = webhookSigner.generateSecret();
        String payload = "{\"event\":\"payment.success\",\"amount\":100.00}";
        String tampered = "{\"event\":\"payment.success\",\"amount\":999.00}";
        long timestamp = Instant.now().getEpochSecond();

        String signatureHeader = webhookSigner.signPayload(payload, timestamp, secret);
        boolean isValid = webhookSigner.verifySignature(tampered, signatureHeader, secret, 300L);

        assertFalse(isValid, "Tampered payload must fail signature verification");
    }

    @Test
    @DisplayName("Signature verification fails if secret is incorrect")
    void testWrongSecretFails() {
        String secret1 = webhookSigner.generateSecret();
        String secret2 = webhookSigner.generateSecret();
        String payload = "{\"event\":\"payment.success\"}";
        long timestamp = Instant.now().getEpochSecond();

        String signatureHeader = webhookSigner.signPayload(payload, timestamp, secret1);
        boolean isValid = webhookSigner.verifySignature(payload, signatureHeader, secret2, 300L);

        assertFalse(isValid, "Wrong secret must fail verification");
    }

    @Test
    @DisplayName("Expired timestamp exceeds tolerance window and is rejected")
    void testExpiredTimestampFails() {
        String secret = webhookSigner.generateSecret();
        String payload = "{\"event\":\"payment.success\"}";
        long expiredTimestamp = Instant.now().getEpochSecond() - 600L; // 10 minutes ago (tolerance is 5 min)

        String signatureHeader = webhookSigner.signPayload(payload, expiredTimestamp, secret);
        boolean isValid = webhookSigner.verifySignature(payload, signatureHeader, secret, 300L);

        assertFalse(isValid, "Expired timestamp must fail freshness validation to prevent replay attacks");
    }
}
