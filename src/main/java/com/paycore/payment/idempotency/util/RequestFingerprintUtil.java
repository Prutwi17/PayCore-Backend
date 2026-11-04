package com.paycore.payment.idempotency.util;

import com.paycore.payment.dto.PaymentCreateRequest;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for computing deterministic SHA-256 fingerprints of payment requests.
 */
public final class RequestFingerprintUtil {

    private RequestFingerprintUtil() {
    }

    /**
     * Computes a canonical SHA-256 hex string from the payment create request.
     */
    public static String computeFingerprint(PaymentCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PaymentCreateRequest must not be null");
        }

        String amountStr = request.getAmount() != null
                ? request.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString()
                : "0.00";
        String currency = request.getCurrency() != null ? request.getCurrency().trim().toUpperCase() : "";
        String method = request.getPaymentMethod() != null ? request.getPaymentMethod().name() : "CARD";
        String customer = request.getCustomerReference() != null ? request.getCustomerReference().trim() : "";
        String desc = request.getDescription() != null ? request.getDescription().trim() : "";

        String canonical = String.format("amount=%s|currency=%s|method=%s|customer=%s|desc=%s",
                amountStr, currency, method, customer, desc);

        return sha256Hex(canonical);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
