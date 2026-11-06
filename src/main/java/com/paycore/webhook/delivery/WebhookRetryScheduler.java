package com.paycore.webhook.delivery;

import com.paycore.webhook.entity.WebhookDeliveryAttempt;
import com.paycore.webhook.repository.WebhookDeliveryAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Periodically processes scheduled webhook delivery retries with exponential backoff.
 */
@Component
public class WebhookRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryScheduler.class);
    private static final int BATCH_SIZE = 20;

    @Value("${paycore.webhook.retry.enabled:true}")
    private boolean retryEnabled;

    private final WebhookDeliveryAttemptRepository deliveryAttemptRepository;
    private final WebhookDeliveryWorker deliveryWorker;

    public WebhookRetryScheduler(WebhookDeliveryAttemptRepository deliveryAttemptRepository,
                                 WebhookDeliveryWorker deliveryWorker) {
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryWorker = deliveryWorker;
    }

    @Scheduled(fixedDelayString = "${paycore.webhook.retry.delay:5000}")
    public void processDueRetries() {
        if (!retryEnabled) {
            return;
        }

        List<WebhookDeliveryAttempt> dueRetries = deliveryAttemptRepository.findDueRetries(Instant.now(), PageRequest.of(0, BATCH_SIZE));
        if (dueRetries.isEmpty()) {
            return;
        }

        log.debug("Found {} webhook delivery retries due for processing", dueRetries.size());

        for (WebhookDeliveryAttempt attempt : dueRetries) {
            try {
                attempt.setAttemptNumber(attempt.getAttemptNumber() + 1);
                deliveryWorker.executeDelivery(attempt);
            } catch (Exception e) {
                log.error("Error retrying webhook delivery [attemptRef={}]: {}", attempt.getAttemptReference(), e.getMessage());
            }
        }
    }
}
