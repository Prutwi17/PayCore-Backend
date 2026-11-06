package com.paycore.event.outbox;

import java.time.Instant;

public class OutboxEventResponseDto {

    private Long id;
    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private Long merchantId;
    private String payload;
    private OutboxStatus status;
    private int retryCount;
    private Instant nextRetryAt;
    private String lastError;
    private Instant createdAt;
    private Instant publishedAt;

    public OutboxEventResponseDto() {
    }

    public static OutboxEventResponseDto fromEntity(OutboxEvent event) {
        OutboxEventResponseDto dto = new OutboxEventResponseDto();
        dto.setId(event.getId());
        dto.setEventId(event.getEventId());
        dto.setAggregateType(event.getAggregateType());
        dto.setAggregateId(event.getAggregateId());
        dto.setEventType(event.getEventType());
        dto.setMerchantId(event.getMerchantId());
        dto.setPayload(event.getPayload());
        dto.setStatus(event.getStatus());
        dto.setRetryCount(event.getRetryCount());
        dto.setNextRetryAt(event.getNextRetryAt());
        dto.setLastError(event.getLastError());
        dto.setCreatedAt(event.getCreatedAt());
        dto.setPublishedAt(event.getPublishedAt());
        return dto;
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

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public void setStatus(OutboxStatus status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
