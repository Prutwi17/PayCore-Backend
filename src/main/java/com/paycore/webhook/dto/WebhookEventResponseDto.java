package com.paycore.webhook.dto;

import com.paycore.webhook.entity.WebhookEvent;

import java.time.Instant;

public class WebhookEventResponseDto {

    private Long id;
    private String eventReference;
    private Long merchantId;
    private String eventType;
    private String aggregateId;
    private String payload;
    private Instant createdAt;

    public WebhookEventResponseDto() {
    }

    public static WebhookEventResponseDto fromEntity(WebhookEvent event) {
        WebhookEventResponseDto dto = new WebhookEventResponseDto();
        dto.setId(event.getId());
        dto.setEventReference(event.getEventReference());
        dto.setMerchantId(event.getMerchantId());
        dto.setEventType(event.getEventType());
        dto.setAggregateId(event.getAggregateId());
        dto.setPayload(event.getPayload());
        dto.setCreatedAt(event.getCreatedAt());
        return dto;
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
