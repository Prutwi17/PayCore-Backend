package com.paycore.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class WebhookEndpointCreateRequest {

    @NotBlank(message = "Destination URL cannot be blank")
    @Size(max = 1024, message = "URL cannot exceed 1024 characters")
    private String url;

    private String subscribedEvents = "*";

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    public WebhookEndpointCreateRequest() {
    }

    public WebhookEndpointCreateRequest(String url, String subscribedEvents, String description) {
        this.url = url;
        this.subscribedEvents = subscribedEvents;
        this.description = description;
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
}
