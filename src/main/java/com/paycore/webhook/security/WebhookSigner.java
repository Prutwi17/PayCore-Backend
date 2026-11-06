package com.paycore.webhook.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Signs outbound webhook payloads using HMAC-SHA256 and provides constant-time signature verification.
 */
@Component
public class WebhookSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    public static final String SECRET_PREFIX = "whsec_";
    public static final long DEFAULT_TOLERANCE_SECONDS = 300L; // 5 minutes

    /**
     * Generates a new 256-bit cryptographically secure webhook signing secret prefixed with whsec_.
     */
    public String generateSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return SECRET_PREFIX + HexFormat.of().formatHex(bytes);
    }

    /**
     * Hashes a signing secret for safe database storage/integrity verification.
     */
    public String hashSecret(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash webhook secret", e);
        }
    }

    /**
     * Signs the payload with the given timestamp and secret.
     * Returns standard header format: t={timestamp},v1={signature}
     */
    public String signPayload(String payload, long timestampSeconds, String secret) {
        String canonicalString = timestampSeconds + "." + (payload != null ? payload : "");
        String signature = computeHmacSha256(canonicalString, secret);
        return "t=" + timestampSeconds + ",v1=" + signature;
    }

    /**
     * Computes raw HMAC-SHA256 hex string.
     */
    public String computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }

    /**
     * Verifies an incoming webhook signature using constant-time comparison and timestamp freshness check.
     */
    public boolean verifySignature(String payload, String signatureHeader, String secret, long toleranceSeconds) {
        if (signatureHeader == null || secret == null) {
            return false;
        }

        long timestamp = -1;
        String providedSignature = null;

        String[] elements = signatureHeader.split(",");
        for (String element : elements) {
            String[] kv = element.trim().split("=", 2);
            if (kv.length == 2) {
                if ("t".equalsIgnoreCase(kv[0])) {
                    try {
                        timestamp = Long.parseLong(kv[1]);
                    } catch (NumberFormatException ignored) {
                        return false;
                    }
                } else if ("v1".equalsIgnoreCase(kv[0])) {
                    providedSignature = kv[1];
                }
            }
        }

        if (timestamp <= 0 || providedSignature == null) {
            return false;
        }

        // Timestamp freshness check
        long currentTimestamp = Instant.now().getEpochSecond();
        if (Math.abs(currentTimestamp - timestamp) > toleranceSeconds) {
            return false;
        }

        // Compute expected signature
        String canonicalString = timestamp + "." + (payload != null ? payload : "");
        String expectedSignature = computeHmacSha256(canonicalString, secret);

        // Constant-time comparison
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
