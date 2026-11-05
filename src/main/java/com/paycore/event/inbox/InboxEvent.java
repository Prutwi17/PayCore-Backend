package com.paycore.event.inbox;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Inbox Event Entity for Idempotent Consumer Processing.
 * Enforces strict deduplication across Kafka consumer instances using a composite unique constraint
 * on (event_id, consumer_name).
 */
@Entity
@Table(
        name = "inbox_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inbox_event_consumer", columnNames = {"event_id", "consumer_name"})
        },
        indexes = {
                @Index(name = "idx_inbox_consumer_status", columnList = "consumer_name, processing_status"),
                @Index(name = "idx_inbox_received", columnList = "received_at")
        }
)
public class InboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "consumer_name", nullable = false, length = 64)
    private String consumerName;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 32)
    private InboxStatus processingStatus = InboxStatus.RECEIVED;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    @Lob
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    public InboxEvent() {
    }

    public InboxEvent(String eventId, String consumerName, String eventType) {
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.eventType = eventType;
        this.processingStatus = InboxStatus.RECEIVED;
        this.receivedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public InboxStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(InboxStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
