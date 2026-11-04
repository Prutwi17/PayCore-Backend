package com.paycore.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRetryExecutorTest {

    @Test
    @DisplayName("Safe query succeeds after transient failures within max attempts")
    void safeQuery_succeedsAfterTransientFailures() {
        PaymentRetryExecutor executor = new PaymentRetryExecutor(3, Duration.ofMillis(10), 2.0, Duration.ofMillis(100));
        AtomicInteger attempts = new AtomicInteger(0);

        String result = executor.executeSafeQuery("testQuery", () -> {
            int current = attempts.incrementAndGet();
            if (current < 3) {
                throw new RuntimeException("Transient network blip");
            }
            return "SUCCESS_DATA";
        });

        assertThat(result).isEqualTo("SUCCESS_DATA");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Safe query exhausts retries and propagates exception when always failing")
    void safeQuery_exhaustsRetriesAndThrows() {
        PaymentRetryExecutor executor = new PaymentRetryExecutor(3, Duration.ofMillis(5), 2.0, Duration.ofMillis(50));
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> executor.executeSafeQuery("failingQuery", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("Permanent provider outage");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("Permanent provider outage");

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Unsafe charge operation is NEVER retried on failure")
    void unsafeCharge_neverRetriedOnFailure() {
        PaymentRetryExecutor executor = new PaymentRetryExecutor(3, Duration.ofMillis(10), 2.0, Duration.ofMillis(100));
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> executor.executeCharge("chargePayment", () -> {
            attempts.incrementAndGet();
            throw new RuntimeException("Simulated socket timeout");
        })).isInstanceOf(RuntimeException.class).hasMessageContaining("Simulated socket timeout");

        assertThat(attempts.get()).isEqualTo(1); // Crucial safety assertion: exactly 1 attempt
    }
}
