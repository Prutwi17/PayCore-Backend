package com.paycore.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Isolated retry executor implementing exponential backoff.
 * Strictly enforces retry classification:
 * - SAFE_QUERY: Eligible for idempotent status polling and verification retries.
 * - UNSAFE_CHARGE: Explicitly prohibited from blind automated retries to prevent double charges.
 */
@Component
public class PaymentRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(PaymentRetryExecutor.class);

    public enum RetryClassification {
        SAFE_QUERY,
        UNSAFE_CHARGE
    }

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final double backoffMultiplier;
    private final Duration maxBackoff;

    public PaymentRetryExecutor() {
        this(3, Duration.ofMillis(100), 2.0, Duration.ofMillis(1000));
    }

    public PaymentRetryExecutor(int maxAttempts, Duration initialBackoff, double backoffMultiplier, Duration maxBackoff) {
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.backoffMultiplier = backoffMultiplier;
        this.maxBackoff = maxBackoff;
    }

    /**
     * Executes an operation classified as SAFE_QUERY with exponential backoff retries.
     */
    public <T> T executeSafeQuery(String operationName, Supplier<T> action) {
        return executeWithRetry(operationName, RetryClassification.SAFE_QUERY, action);
    }

    /**
     * Executes a payment charge operation. Under the safety invariant, blind retries are strictly disallowed.
     */
    public <T> T executeCharge(String operationName, Supplier<T> action) {
        return executeWithRetry(operationName, RetryClassification.UNSAFE_CHARGE, action);
    }

    private <T> T executeWithRetry(String operationName, RetryClassification classification, Supplier<T> action) {
        if (classification == RetryClassification.UNSAFE_CHARGE) {
            // Charges must NEVER be blindly retried on failure
            try {
                return action.get();
            } catch (Exception ex) {
                log.warn("Charge operation [{}] failed: {}. Blind retries are strictly prohibited to prevent duplicate payment initiation.",
                        operationName, ex.getMessage());
                throw ex;
            }
        }

        // SAFE_QUERY retry loop
        long currentBackoffMs = initialBackoff.toMillis();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (attempt > 1) {
                    log.info("Executing retry [{}/{}] for safe query [{}] after backoff [{}ms]",
                            attempt, maxAttempts, operationName, currentBackoffMs);
                }
                return action.get();
            } catch (Exception ex) {
                lastException = ex;
                log.warn("Safe query [{}] attempt [{}/{}] failed: {}",
                        operationName, attempt, maxAttempts, ex.getMessage());

                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(currentBackoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Retry thread interrupted during backoff", ie);
                    }
                    currentBackoffMs = Math.min((long) (currentBackoffMs * backoffMultiplier), maxBackoff.toMillis());
                }
            }
        }

        log.error("Exhausted all [{}] retry attempts for safe query [{}]", maxAttempts, operationName);
        if (lastException instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException("Safe query operation [" + operationName + "] failed after " + maxAttempts + " attempts", lastException);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
