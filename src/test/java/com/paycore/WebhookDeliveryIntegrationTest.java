package com.paycore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.event.model.EventType;
import com.paycore.event.model.PayCoreDomainEvent;
import com.paycore.webhook.delivery.WebhookDeliveryWorker;
import com.paycore.webhook.delivery.WebhookDispatcherService;
import com.paycore.webhook.entity.EndpointStatus;
import com.paycore.webhook.entity.WebhookDeliveryAttempt;
import com.paycore.webhook.entity.WebhookDeliveryStatus;
import com.paycore.webhook.entity.WebhookEndpoint;
import com.paycore.webhook.repository.WebhookDeliveryAttemptRepository;
import com.paycore.webhook.repository.WebhookEndpointRepository;
import com.paycore.webhook.security.WebhookSigner;
import com.paycore.webhook.security.WebhookSsrfValidator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Webhook Delivery Engine Integration Test with Local HTTP Server")
class WebhookDeliveryIntegrationTest {

    private static HttpServer localServer;
    private static int serverPort;
    private static final List<Map<String, String>> receivedHeaders = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> receivedBodies = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger responseCodeToReturn = new AtomicInteger(200);

    @Autowired
    private WebhookDispatcherService dispatcherService;

    @Autowired
    private WebhookEndpointRepository endpointRepository;

    @Autowired
    private WebhookDeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private WebhookSsrfValidator ssrfValidator;

    @Autowired
    private WebhookSigner webhookSigner;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startHttpServer() throws IOException {
        localServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverPort = localServer.getAddress().getPort();
        localServer.createContext("/webhook-receiver", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                Map<String, String> headers = new HashMap<>();
                exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
                receivedHeaders.add(headers);

                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                receivedBodies.add(body);

                int code = responseCodeToReturn.get();
                byte[] responseBytes = ("{\"status\":\"received\"}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(code, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        });
        localServer.setExecutor(null);
        localServer.start();
    }

    @AfterAll
    static void stopHttpServer() {
        if (localServer != null) {
            localServer.stop(0);
        }
    }

    @Test
    @DisplayName("End-to-end webhook dispatch: signs payload, sends HTTP POST, verifies signature, marks SUCCESS")
    void testEndToEndWebhookDeliverySuccess() throws Exception {
        // Temporarily allow local loopback for the test server
        ssrfValidator.setAllowLocalLoopback(true);
        responseCodeToReturn.set(200);
        receivedHeaders.clear();
        receivedBodies.clear();

        Long merchantId = 999L;
        String rawSecret = webhookSigner.generateSecret();
        String secretHash = webhookSigner.hashSecret(rawSecret);
        String secretEnc = Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8));

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setEndpointReference("ep_test_receiver_" + UUID.randomUUID().toString().substring(0, 8));
        endpoint.setMerchantId(merchantId);
        endpoint.setUrl("http://127.0.0.1:" + serverPort + "/webhook-receiver");
        endpoint.setSecretPrefix("whsec_");
        endpoint.setSecretHash(secretHash);
        endpoint.setSecretEncrypted(secretEnc);
        endpoint.setSubscribedEvents("PAYMENT_SUCCESS");
        endpoint.setStatus(EndpointStatus.ACTIVE);
        endpointRepository.save(endpoint);

        // Build domain event
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentReference", "pay_webhook_test_1");
        payload.put("amount", "250.00");
        payload.put("currency", "USD");
        payload.put("status", "SUCCESS");

        PayCoreDomainEvent event = PayCoreDomainEvent.builder()
                .eventId("evt_webhook_test_1")
                .eventType(EventType.PAYMENT_SUCCESS)
                .aggregateType("PAYMENT")
                .aggregateId("pay_webhook_test_1")
                .merchantId(merchantId)
                .occurredAt(Instant.now())
                .payload(payload)
                .build();

        // Dispatch domain event through dispatcher service
        List<WebhookDeliveryAttempt> attempts = dispatcherService.dispatchDomainEvent(event);

        assertEquals(1, attempts.size());
        WebhookDeliveryAttempt attempt = attempts.get(0);

        // Verify attempt outcome
        assertEquals(WebhookDeliveryStatus.SUCCESS, attempt.getDeliveryStatus());
        assertEquals(200, attempt.getHttpStatusCode());
        assertNotNull(attempt.getExecutionDurationMs());

        // Verify local server received HTTP request
        assertEquals(1, receivedHeaders.size());
        Map<String, String> headers = receivedHeaders.get(0);
        assertTrue("PayCore-Webhook/1.0".equalsIgnoreCase(headers.get("user-agent")));
        assertEquals("evt_webhook_test_1", headers.get("x-paycore-event-id"));
        assertEquals("PAYMENT_SUCCESS", headers.get("x-paycore-event-type"));

        // Verify signature received by the endpoint
        String signatureHeader = headers.get("x-paycore-signature");
        assertNotNull(signatureHeader);
        String receivedBody = receivedBodies.get(0);

        boolean signatureValid = webhookSigner.verifySignature(receivedBody, signatureHeader, rawSecret, 300L);
        assertTrue(signatureValid, "Recipient server must successfully verify HMAC-SHA256 signature");
    }

    @Test
    @DisplayName("Webhook dispatch failure (HTTP 500) sets RETRYING status and schedules exponential backoff")
    void testWebhookDeliveryFailureSchedulesRetry() {
        ssrfValidator.setAllowLocalLoopback(true);
        responseCodeToReturn.set(500);

        Long merchantId = 888L;
        String rawSecret = webhookSigner.generateSecret();
        String secretHash = webhookSigner.hashSecret(rawSecret);
        String secretEnc = Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8));

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setEndpointReference("ep_fail_receiver_" + UUID.randomUUID().toString().substring(0, 8));
        endpoint.setMerchantId(merchantId);
        endpoint.setUrl("http://127.0.0.1:" + serverPort + "/webhook-receiver");
        endpoint.setSecretPrefix("whsec_");
        endpoint.setSecretHash(secretHash);
        endpoint.setSecretEncrypted(secretEnc);
        endpoint.setSubscribedEvents("*");
        endpoint.setStatus(EndpointStatus.ACTIVE);
        endpointRepository.save(endpoint);

        PayCoreDomainEvent event = PayCoreDomainEvent.builder()
                .eventId("evt_fail_test_" + UUID.randomUUID())
                .eventType(EventType.PAYMENT_FAILED)
                .aggregateType("PAYMENT")
                .aggregateId("pay_fail_test")
                .merchantId(merchantId)
                .occurredAt(Instant.now())
                .payload(Map.of("reason", "Card declined"))
                .build();

        List<WebhookDeliveryAttempt> attempts = dispatcherService.dispatchDomainEvent(event);
        assertEquals(1, attempts.size());

        WebhookDeliveryAttempt attempt = attempts.get(0);
        assertEquals(WebhookDeliveryStatus.RETRYING, attempt.getDeliveryStatus());
        assertEquals(500, attempt.getHttpStatusCode());
        assertNotNull(attempt.getNextRetryAt(), "Failed retryable delivery must have nextRetryAt scheduled");
        assertTrue(attempt.getNextRetryAt().isAfter(Instant.now()));
    }
}
