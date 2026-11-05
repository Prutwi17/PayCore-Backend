package com.paycore.webhook.delivery;

import com.paycore.webhook.entity.EndpointStatus;
import com.paycore.webhook.entity.WebhookDeliveryAttempt;
import com.paycore.webhook.entity.WebhookDeliveryStatus;
import com.paycore.webhook.entity.WebhookEndpoint;
import com.paycore.webhook.repository.WebhookDeliveryAttemptRepository;
import com.paycore.webhook.repository.WebhookEndpointRepository;
import com.paycore.webhook.security.WebhookSigner;
import com.paycore.webhook.security.WebhookSsrfValidator;
import com.paycore.webhook.service.WebhookEndpointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Worker responsible for executing signed HTTP POST requests to merchant webhook destinations
 * with SSRF defense, timing metrics, and retry/dead-letter classification.
 */
@Component
public class WebhookDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryWorker.class);

    public static final int MAX_ATTEMPTS = 5;
    // Exponential backoff schedule in seconds: [5s, 30s, 2m, 15m, 1h]
    private static final int[] BACKOFF_SCHEDULE_SECONDS = {5, 30, 120, 900, 3600};

    private final WebhookDeliveryAttemptRepository deliveryAttemptRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookEndpointService endpointService;
    private final WebhookSigner webhookSigner;
    private final WebhookSsrfValidator ssrfValidator;
    private final RestTemplate restTemplate;

    public WebhookDeliveryWorker(WebhookDeliveryAttemptRepository deliveryAttemptRepository,
                                 WebhookEndpointRepository endpointRepository,
                                 WebhookEndpointService endpointService,
                                 WebhookSigner webhookSigner,
                                 WebhookSsrfValidator ssrfValidator,
                                 RestTemplateBuilder restTemplateBuilder) {
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.endpointRepository = endpointRepository;
        this.endpointService = endpointService;
        this.webhookSigner = webhookSigner;
        this.ssrfValidator = ssrfValidator;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(5000))
                .setReadTimeout(Duration.ofMillis(10000))
                .build();
    }

    public WebhookDeliveryAttempt executeDelivery(WebhookDeliveryAttempt attempt) {
        WebhookEndpoint endpoint = attempt.getEndpoint();
        String destinationUrl = attempt.getRequestUrl();
        String payload = attempt.getWebhookEvent().getPayload();
        String eventId = attempt.getWebhookEvent().getEventReference();
        String eventType = attempt.getWebhookEvent().getEventType();

        attempt.setAttemptedAt(Instant.now());
        long startTime = System.currentTimeMillis();

        try {
            // Re-validate destination URL against SSRF right before network invocation
            ssrfValidator.validateUrl(destinationUrl);

            String secret = endpointService.resolvePlainSecret(endpoint);
            long timestamp = Instant.now().getEpochSecond();
            String signatureHeader = webhookSigner.signPayload(payload, timestamp, secret);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "PayCore-Webhook/1.0");
            headers.set("X-PayCore-Event-Id", eventId);
            headers.set("X-PayCore-Event-Type", eventType);
            headers.set("X-PayCore-Signature", signatureHeader);

            HttpEntity<String> entity = new HttpEntity<>(payload, headers);

            log.info("Dispatching webhook [attemptRef={}, eventId={}, endpointRef={}] to {}",
                    attempt.getAttemptReference(), eventId, endpoint.getEndpointReference(), destinationUrl);

            ResponseEntity<String> response = restTemplate.exchange(destinationUrl, HttpMethod.POST, entity, String.class);
            long duration = System.currentTimeMillis() - startTime;

            attempt.setExecutionDurationMs(duration);
            attempt.setHttpStatusCode(response.getStatusCode().value());
            attempt.setResponseBody(truncate(response.getBody(), 1000));

            if (response.getStatusCode().is2xxSuccessful()) {
                attempt.setDeliveryStatus(WebhookDeliveryStatus.SUCCESS);
                attempt.setFailureReason(null);
                attempt.setNextRetryAt(null);
                endpoint.setFailureCount(0);
                endpointRepository.save(endpoint);
                log.info("Webhook delivered successfully [attemptRef={}, status={}] in {}ms",
                        attempt.getAttemptReference(), response.getStatusCode().value(), duration);
            } else {
                handleFailure(attempt, endpoint, response.getStatusCode().value(), "Non-2xx response: " + response.getStatusCode());
            }
        } catch (HttpStatusCodeException ex) {
            long duration = System.currentTimeMillis() - startTime;
            attempt.setExecutionDurationMs(duration);
            attempt.setHttpStatusCode(ex.getStatusCode().value());
            attempt.setResponseBody(truncate(ex.getResponseBodyAsString(), 1000));
            handleFailure(attempt, endpoint, ex.getStatusCode().value(), ex.getMessage());
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            attempt.setExecutionDurationMs(duration);
            attempt.setHttpStatusCode(null);
            handleFailure(attempt, endpoint, null, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }

        return deliveryAttemptRepository.save(attempt);
    }

    private void handleFailure(WebhookDeliveryAttempt attempt, WebhookEndpoint endpoint, Integer statusCode, String errorMessage) {
        endpoint.setFailureCount(endpoint.getFailureCount() + 1);
        endpointRepository.save(endpoint);

        attempt.setFailureReason(truncate(errorMessage, 1000));
        boolean retryable = isRetryable(statusCode, errorMessage);

        if (retryable && attempt.getAttemptNumber() < MAX_ATTEMPTS) {
            int backoffIdx = Math.min(attempt.getAttemptNumber() - 1, BACKOFF_SCHEDULE_SECONDS.length - 1);
            int backoffSeconds = BACKOFF_SCHEDULE_SECONDS[backoffIdx];
            attempt.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);
            attempt.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
            log.warn("Webhook delivery failed [attemptRef={}, attempt={}/{}]. Scheduling retry in {}s. Reason: {}",
                    attempt.getAttemptReference(), attempt.getAttemptNumber(), MAX_ATTEMPTS, backoffSeconds, errorMessage);
        } else {
            attempt.setDeliveryStatus(retryable ? WebhookDeliveryStatus.DEAD_LETTER : WebhookDeliveryStatus.FAILED);
            attempt.setNextRetryAt(null);
            log.error("Webhook delivery exhausted/failed permanently [attemptRef={}, status={}]. Reason: {}",
                    attempt.getAttemptReference(), attempt.getDeliveryStatus(), errorMessage);
        }
    }

    public boolean isRetryable(Integer statusCode, String errorMessage) {
        if (errorMessage != null && errorMessage.contains("SSRF_VALIDATION_ERROR")) {
            return false;
        }
        if (statusCode == null) {
            // Network timeouts, connection drops, socket resets are retryable
            return true;
        }
        if (statusCode == 429) {
            // Rate limit is explicitly retryable
            return true;
        }
        if (statusCode >= 500 && statusCode <= 599) {
            // Server errors are retryable
            return true;
        }
        // Most 4xx (400, 401, 403, 404, 405, 410) are non-retryable client errors
        return false;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...[TRUNCATED]";
    }
}
