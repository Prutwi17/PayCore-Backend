package com.paycore.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paycore.webhook.entity.EndpointStatus;
import com.paycore.webhook.entity.WebhookEndpoint;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookEndpointResponseDto {

    private Long id;
    private String endpointReference;
    private Long merchantId;
    private String url;
    private String secretPrefix;
    private String maskedSecret;
    private String secret; // Shown only ONCE upon initial creation or rotation!
    private String subscribedEvents;
    private EndpointStatus status;
    private String description;
    private int failureCount;
    private Instant createdAt;
    private Instant updatedAt;

    public WebhookEndpointResponseDto() {
    }

    public static WebhookEndpointResponseDto fromEntity(WebhookEndpoint endpoint, String oneTimeSecret) {
        WebhookEndpointResponseDto dto = new WebhookEndpointResponseDto();
        dto.setId(endpoint.getId());
        dto.setEndpointReference(endpoint.getEndpointReference());
        dto.setMerchantId(endpoint.getMerchantId());
        dto.setUrl(endpoint.getUrl());
        dto.setSecretPrefix(endpoint.getSecretPrefix());
        dto.setMaskedSecret(endpoint.getSecretPrefix() + "********************************");
        dto.setSecret(oneTimeSecret); // Null in standard gets/lists
        dto.setSubscribedEvents(endpoint.getSubscribedEvents());
        dto.setStatus(endpoint.getStatus());
        dto.setDescription(endpoint.getDescription());
        dto.setFailureCount(endpoint.getFailureCount());
        dto.setCreatedAt(endpoint.getCreatedAt());
        dto.setUpdatedAt(endpoint.getUpdatedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEndpointReference() {
        return endpointReference;
    }

    public void setEndpointReference(String endpointReference) {
        this.endpointReference = endpointReference;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecretPrefix() {
        return secretPrefix;
    }

    public void setSecretPrefix(String secretPrefix) {
        this.secretPrefix = secretPrefix;
    }

    public String getMaskedSecret() {
        return maskedSecret;
    }

    public void setMaskedSecret(String maskedSecret) {
        this.maskedSecret = maskedSecret;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getSubscribedEvents() {
        return subscribedEvents;
    }

    public void setSubscribedEvents(String subscribedEvents) {
        this.subscribedEvents = subscribedEvents;
    }

    public EndpointStatus getStatus() {
        return status;
    }

    public void setStatus(EndpointStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
