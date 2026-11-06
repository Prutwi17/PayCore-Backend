package com.paycore.webhook.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "webhook_events",
        indexes = {
                @Index(name = "idx_webhook_events_merchant", columnList = "merchant_id, created_at"),
                @Index(name = "idx_webhook_events_ref", columnList = "event_reference"),
                @Index(name = "idx_webhook_events_type", columnList = "event_type")
        }
)
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_reference", nullable = false, unique = true, length = 64)
    private String eventReference;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public WebhookEvent() {
    }

    public WebhookEvent(String eventReference, Long merchantId, String eventType, String aggregateId, String payload) {
        this.eventReference = eventReference;
        this.merchantId = merchantId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventReference() {
        return eventReference;
    }

    public void setEventReference(String eventReference) {
        this.eventReference = eventReference;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
