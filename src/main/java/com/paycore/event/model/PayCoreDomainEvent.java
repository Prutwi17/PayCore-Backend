package com.paycore.event.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable canonical PayCore domain event structure.
 * Guaranteed not to contain sensitive data (passwords, JWTs, API keys, raw card data).
 */
public final class PayCoreDomainEvent {

    private final String eventId;
    private final EventType eventType;
    private final String aggregateType;
    private final String aggregateId;
    private final Long merchantId;
    private final int eventVersion;
    private final Instant occurredAt;
    private final Map<String, Object> payload;

    @JsonCreator
    public PayCoreDomainEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("eventType") EventType eventType,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("aggregateId") String aggregateId,
            @JsonProperty("merchantId") Long merchantId,
            @JsonProperty("eventVersion") int eventVersion,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("payload") Map<String, Object> payload) {
        this.eventId = eventId != null ? eventId : "evt_" + UUID.randomUUID().toString().replace("-", "");
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType cannot be null");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId cannot be null");
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId cannot be null");
        this.eventVersion = eventVersion > 0 ? eventVersion : 1;
        this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
        this.payload = payload != null ? Collections.unmodifiableMap(payload) : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getEventId() {
        return eventId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public static class Builder {
        private String eventId;
        private EventType eventType;
        private String aggregateType;
        private String aggregateId;
        private Long merchantId;
        private int eventVersion = 1;
        private Instant occurredAt = Instant.now();
        private Map<String, Object> payload;

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder eventType(EventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder aggregateType(String aggregateType) {
            this.aggregateType = aggregateType;
            return this;
        }

        public Builder aggregateId(String aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public Builder merchantId(Long merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public Builder eventVersion(int eventVersion) {
            this.eventVersion = eventVersion;
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public PayCoreDomainEvent build() {
            return new PayCoreDomainEvent(eventId, eventType, aggregateType, aggregateId, merchantId, eventVersion, occurredAt, payload);
        }
    }
}
