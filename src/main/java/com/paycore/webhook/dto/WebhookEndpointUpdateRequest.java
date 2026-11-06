package com.paycore.webhook.dto;

import com.paycore.webhook.entity.EndpointStatus;
import jakarta.validation.constraints.Size;

public class WebhookEndpointUpdateRequest {

    @Size(max = 1024, message = "URL cannot exceed 1024 characters")
    private String url;

    private String subscribedEvents;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    private EndpointStatus status;

    public WebhookEndpointUpdateRequest() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSubscribedEvents() {
        return subscribedEvents;
    }

    public void setSubscribedEvents(String subscribedEvents) {
        this.subscribedEvents = subscribedEvents;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public EndpointStatus getStatus() {
        return status;
    }

    public void setStatus(EndpointStatus status) {
        this.status = status;
    }
}
