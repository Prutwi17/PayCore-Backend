package com.paycore.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.event.kafka.KafkaProducerService;
import com.paycore.event.model.PayCoreDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Periodically polls unpublished outbox events and publishes them to Apache Kafka.
 * Guarantees at-least-once delivery; duplicates are safely handled downstream by inbox consumers.
 */
@Component
public class OutboxPublisherScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherScheduler.class);
    private static final int BATCH_SIZE = 50;
    private static final long MAX_BACKOFF_SECONDS = 300L; // 5 minutes

    @Value("${paycore.outbox.publisher.enabled:true}")
    private boolean publisherEnabled;

    private final OutboxService outboxService;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    public OutboxPublisherScheduler(OutboxService outboxService, KafkaProducerService kafkaProducerService, ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.kafkaProducerService = kafkaProducerService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${paycore.outbox.publisher.delay:1000}")
    public void publishPendingEvents() {
        if (!publisherEnabled) {
            return;
        }

        List<OutboxEvent> pending = outboxService.findUnpublishedEvents(PageRequest.of(0, BATCH_SIZE));
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events for publishing", pending.size());

        for (OutboxEvent event : pending) {
            publishSingleEvent(event);
        }
    }

    public boolean publishSingleEvent(OutboxEvent outboxEvent) {
        try {
            PayCoreDomainEvent domainEvent = objectMapper.readValue(outboxEvent.getPayload(), PayCoreDomainEvent.class);
            
            // Wait up to 3 seconds for Kafka confirmation
            kafkaProducerService.publishEvent(domainEvent).get(3000, TimeUnit.MILLISECONDS);
            
            outboxService.markPublished(outboxEvent.getEventId());
            return true;
        } catch (Exception e) {
            int nextRetryCount = outboxEvent.getRetryCount() + 1;
            long backoffSeconds = Math.min(MAX_BACKOFF_SECONDS, (long) Math.pow(2, Math.min(nextRetryCount, 10)));
            Instant nextRetryAt = Instant.now().plusSeconds(backoffSeconds);

            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Failed to publish outbox event [id={}] (attempt={}): {}. Scheduling retry in {}s",
                    outboxEvent.getEventId(), nextRetryCount, errorMessage, backoffSeconds);

            outboxService.markFailed(outboxEvent.getEventId(), errorMessage, nextRetryCount, nextRetryAt);
            return false;
        }
    }
}
