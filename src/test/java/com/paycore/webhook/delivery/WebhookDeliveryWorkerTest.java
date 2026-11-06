package com.paycore.webhook.delivery;

import com.paycore.webhook.repository.WebhookDeliveryAttemptRepository;
import com.paycore.webhook.repository.WebhookEndpointRepository;
import com.paycore.webhook.security.WebhookSigner;
import com.paycore.webhook.security.WebhookSsrfValidator;
import com.paycore.webhook.service.WebhookEndpointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook Delivery Worker Unit Tests")
class WebhookDeliveryWorkerTest {

    @Mock
    private WebhookDeliveryAttemptRepository deliveryAttemptRepository;

    @Mock
    private WebhookEndpointRepository endpointRepository;

    @Mock
    private WebhookEndpointService endpointService;

    @Mock
    private WebhookSigner webhookSigner;

    @Mock
    private WebhookSsrfValidator ssrfValidator;

    private WebhookDeliveryWorker deliveryWorker;

    @BeforeEach
    void setUp() {
        deliveryWorker = new WebhookDeliveryWorker(
                deliveryAttemptRepository,
                endpointRepository,
                endpointService,
                webhookSigner,
                ssrfValidator,
                new RestTemplateBuilder()
        );
    }

    @Test
    @DisplayName("Network timeout and socket exception (null status code) is retryable")
    void testNetworkTimeoutIsRetryable() {
        assertTrue(deliveryWorker.isRetryable(null, "SocketTimeoutException: Read timed out"));
        assertTrue(deliveryWorker.isRetryable(null, "ConnectException: Connection refused"));
    }

    @Test
    @DisplayName("HTTP 429 Rate Limited is retryable")
    void testRateLimitIsRetryable() {
        assertTrue(deliveryWorker.isRetryable(429, "Too Many Requests"));
    }

    @Test
    @DisplayName("HTTP 5xx Server Errors (500, 502, 503, 504) are retryable")
    void test5xxIsRetryable() {
        assertTrue(deliveryWorker.isRetryable(500, "Internal Server Error"));
        assertTrue(deliveryWorker.isRetryable(502, "Bad Gateway"));
        assertTrue(deliveryWorker.isRetryable(503, "Service Unavailable"));
        assertTrue(deliveryWorker.isRetryable(504, "Gateway Timeout"));
    }

    @Test
    @DisplayName("HTTP 4xx Client Errors (400, 401, 403, 404, 410) are non-retryable")
    void test4xxIsNonRetryable() {
        assertFalse(deliveryWorker.isRetryable(400, "Bad Request"));
        assertFalse(deliveryWorker.isRetryable(401, "Unauthorized"));
        assertFalse(deliveryWorker.isRetryable(403, "Forbidden"));
        assertFalse(deliveryWorker.isRetryable(404, "Not Found"));
        assertFalse(deliveryWorker.isRetryable(410, "Gone"));
    }

    @Test
    @DisplayName("SSRF validation error is non-retryable")
    void testSsrfErrorIsNonRetryable() {
        assertFalse(deliveryWorker.isRetryable(null, "SSRF_VALIDATION_ERROR: Destination resolves to a loopback address"));
    }
}
