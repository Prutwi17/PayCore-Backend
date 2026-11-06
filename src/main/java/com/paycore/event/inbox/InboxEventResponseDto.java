package com.paycore.event.inbox;

import java.time.Instant;

public class InboxEventResponseDto {

    private Long id;
    private String eventId;
    private String consumerName;
    private String eventType;
    private InboxStatus processingStatus;
    private Instant receivedAt;
    private Instant processedAt;
    private String failureReason;

    public InboxEventResponseDto() {
    }

    public static InboxEventResponseDto fromEntity(InboxEvent event) {
        InboxEventResponseDto dto = new InboxEventResponseDto();
        dto.setId(event.getId());
        dto.setEventId(event.getEventId());
        dto.setConsumerName(event.getConsumerName());
        dto.setEventType(event.getEventType());
        dto.setProcessingStatus(event.getProcessingStatus());
        dto.setReceivedAt(event.getReceivedAt());
        dto.setProcessedAt(event.getProcessedAt());
        dto.setFailureReason(event.getFailureReason());
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
