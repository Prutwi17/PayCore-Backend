package com.paycore.event.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.config.kafka.KafkaTopicConfig;
import com.paycore.event.model.PayCoreDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes serialized domain events to Apache Kafka with consistent partition keys.
 */
@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a domain event to the appropriate Kafka topic with its aggregateId as partition key.
     */
    public CompletableFuture<SendResult<String, String>> publishEvent(PayCoreDomainEvent event) {
        String topic = resolveTopic(event.getAggregateType());
        String partitionKey = event.getAggregateId();

        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            log.info("Publishing event [id={}, type={}, aggregateId={}] to Kafka topic [{}]",
                    event.getEventId(), event.getEventType(), partitionKey, topic);

            return kafkaTemplate.send(topic, partitionKey, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish event [id={}] to topic [{}]: {}", event.getEventId(), topic, ex.getMessage());
                        } else {
                            log.debug("Successfully published event [id={}] to topic [{}] partition [{}] offset [{}]",
                                    event.getEventId(), topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize event [id={}]: {}", event.getEventId(), e.getMessage());
            CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    /**
     * Sends an exhausted failed message to a Dead Letter Queue (DLQ) topic.
     */
    public CompletableFuture<SendResult<String, String>> sendToDlq(String dlqTopic, String partitionKey, String message, String failureReason) {
        log.warn("Routing message [key={}] to DLQ topic [{}] due to: {}", partitionKey, dlqTopic, failureReason);
        return kafkaTemplate.send(dlqTopic, partitionKey, message);
    }

    private String resolveTopic(String aggregateType) {
        if ("PAYMENT".equalsIgnoreCase(aggregateType)) {
            return KafkaTopicConfig.PAYMENT_EVENTS_TOPIC;
        } else if ("REFUND".equalsIgnoreCase(aggregateType)) {
            return KafkaTopicConfig.REFUND_EVENTS_TOPIC;
        }
        return KafkaTopicConfig.WEBHOOK_EVENTS_TOPIC;
    }
}
