package com.paycore.config.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares Kafka topics for PayCore event streaming.
 */
@Configuration
@Profile("!test")
@ConditionalOnProperty(name = "paycore.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    public static final String PAYMENT_EVENTS_TOPIC = "paycore.payment.events";
    public static final String REFUND_EVENTS_TOPIC = "paycore.refund.events";
    public static final String WEBHOOK_EVENTS_TOPIC = "paycore.webhook.events";

    public static final String PAYMENT_EVENTS_DLQ_TOPIC = "paycore.payment.events.dlq";
    public static final String REFUND_EVENTS_DLQ_TOPIC = "paycore.refund.events.dlq";
    public static final String WEBHOOK_EVENTS_DLQ_TOPIC = "paycore.webhook.events.dlq";

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(PAYMENT_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic refundEventsTopic() {
        return TopicBuilder.name(REFUND_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic webhookEventsTopic() {
        return TopicBuilder.name(WEBHOOK_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentEventsDlqTopic() {
        return TopicBuilder.name(PAYMENT_EVENTS_DLQ_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic refundEventsDlqTopic() {
        return TopicBuilder.name(REFUND_EVENTS_DLQ_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic webhookEventsDlqTopic() {
        return TopicBuilder.name(WEBHOOK_EVENTS_DLQ_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
