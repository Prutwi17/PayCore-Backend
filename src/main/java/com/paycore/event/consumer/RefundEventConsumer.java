package com.paycore.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.config.kafka.KafkaTopicConfig;
import com.paycore.event.inbox.InboxService;
import com.paycore.event.kafka.KafkaProducerService;
import com.paycore.event.model.PayCoreDomainEvent;
import com.paycore.webhook.delivery.WebhookDispatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Idempotent consumer for refund lifecycle events published to Apache Kafka.
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "paycore.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class RefundEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RefundEventConsumer.class);
    public static final String CONSUMER_NAME = "RefundEventConsumer";

    private final InboxService inboxService;
    private final WebhookDispatcherService webhookDispatcherService;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    public RefundEventConsumer(InboxService inboxService,
                               WebhookDispatcherService webhookDispatcherService,
                               KafkaProducerService kafkaProducerService,
                               ObjectMapper objectMapper) {
        this.inboxService = inboxService;
        this.webhookDispatcherService = webhookDispatcherService;
        this.kafkaProducerService = kafkaProducerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopicConfig.REFUND_EVENTS_TOPIC, groupId = "paycore-refund-consumer-group")
    public void consume(String message) {
        log.debug("Received refund event from Kafka: {}", message);

        PayCoreDomainEvent event;
        try {
            event = objectMapper.readValue(message, PayCoreDomainEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize refund event payload. Routing to DLQ: {}", e.getMessage());
            kafkaProducerService.sendToDlq(KafkaTopicConfig.REFUND_EVENTS_DLQ_TOPIC, "corrupt_payload", message, e.getMessage());
            return;
        }

        String eventId = event.getEventId();

        // Enforce consumer idempotency via Inbox pattern
        boolean isNew = inboxService.registerReceived(eventId, CONSUMER_NAME, event.getEventType().name());
        if (!isNew) {
            log.info("RefundEventConsumer duplicate event detected [eventId={}]. Skipping.", eventId);
            return;
        }

        try {
            // Fan out event to subscribed merchant webhook endpoints
            webhookDispatcherService.dispatchDomainEvent(event);
            inboxService.markProcessed(eventId, CONSUMER_NAME);
            log.info("RefundEventConsumer successfully processed event [id={}, type={}]", eventId, event.getEventType());
        } catch (Exception e) {
            log.error("RefundEventConsumer error processing event [id={}]: {}", eventId, e.getMessage(), e);
            inboxService.markFailed(eventId, CONSUMER_NAME, e.getMessage());
            kafkaProducerService.sendToDlq(KafkaTopicConfig.REFUND_EVENTS_DLQ_TOPIC, event.getAggregateId(), message, e.getMessage());
        }
    }
}
