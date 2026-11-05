package com.paycore.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.config.kafka.KafkaTopicConfig;
import com.paycore.event.inbox.InboxService;
import com.paycore.event.kafka.KafkaProducerService;
import com.paycore.event.model.PayCoreDomainEvent;
import com.paycore.webhook.delivery.WebhookDispatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Idempotent consumer for payment lifecycle events published to Apache Kafka.
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    public static final String CONSUMER_NAME = "PaymentEventConsumer";

    private final InboxService inboxService;
    private final WebhookDispatcherService webhookDispatcherService;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(InboxService inboxService,
                                WebhookDispatcherService webhookDispatcherService,
                                KafkaProducerService kafkaProducerService,
                                ObjectMapper objectMapper) {
        this.inboxService = inboxService;
        this.webhookDispatcherService = webhookDispatcherService;
        this.kafkaProducerService = kafkaProducerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopicConfig.PAYMENT_EVENTS_TOPIC, groupId = "paycore-payment-consumer-group")
    public void consume(String message) {
        log.debug("Received payment event from Kafka: {}", message);

        PayCoreDomainEvent event;
        try {
            event = objectMapper.readValue(message, PayCoreDomainEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize payment event payload. Routing to DLQ: {}", e.getMessage());
            kafkaProducerService.sendToDlq(KafkaTopicConfig.PAYMENT_EVENTS_DLQ_TOPIC, "corrupt_payload", message, e.getMessage());
            return;
        }

        String eventId = event.getEventId();

        // Enforce consumer idempotency via Inbox pattern
        boolean isNew = inboxService.registerReceived(eventId, CONSUMER_NAME, event.getEventType().name());
        if (!isNew) {
            log.info("PaymentEventConsumer duplicate event detected [eventId={}]. Skipping.", eventId);
            return;
        }

        try {
            // Fan out event to subscribed merchant webhook endpoints
            webhookDispatcherService.dispatchDomainEvent(event);
            inboxService.markProcessed(eventId, CONSUMER_NAME);
            log.info("PaymentEventConsumer successfully processed event [id={}, type={}]", eventId, event.getEventType());
        } catch (Exception e) {
            log.error("PaymentEventConsumer error processing event [id={}]: {}", eventId, e.getMessage(), e);
            inboxService.markFailed(eventId, CONSUMER_NAME, e.getMessage());
            kafkaProducerService.sendToDlq(KafkaTopicConfig.PAYMENT_EVENTS_DLQ_TOPIC, event.getAggregateId(), message, e.getMessage());
        }
    }
}
