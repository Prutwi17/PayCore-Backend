package com.paycore.webhook.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.event.model.PayCoreDomainEvent;
import com.paycore.webhook.entity.EndpointStatus;
import com.paycore.webhook.entity.WebhookDeliveryAttempt;
import com.paycore.webhook.entity.WebhookEndpoint;
import com.paycore.webhook.entity.WebhookEvent;
import com.paycore.webhook.repository.WebhookDeliveryAttemptRepository;
import com.paycore.webhook.repository.WebhookEndpointRepository;
import com.paycore.webhook.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fans out incoming domain events to all subscribed active merchant webhook endpoints.
 */
@Service
public class WebhookDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcherService.class);

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookEventRepository eventRepository;
    private final WebhookDeliveryAttemptRepository deliveryAttemptRepository;
    private final WebhookDeliveryWorker deliveryWorker;
    private final ObjectMapper objectMapper;

    public WebhookDispatcherService(WebhookEndpointRepository endpointRepository,
                                    WebhookEventRepository eventRepository,
                                    WebhookDeliveryAttemptRepository deliveryAttemptRepository,
                                    WebhookDeliveryWorker deliveryWorker,
                                    ObjectMapper objectMapper) {
        this.endpointRepository = endpointRepository;
        this.eventRepository = eventRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryWorker = deliveryWorker;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<WebhookDeliveryAttempt> dispatchDomainEvent(PayCoreDomainEvent domainEvent) {
        Long merchantId = domainEvent.getMerchantId();
        String eventType = domainEvent.getEventType().name();

        List<WebhookEndpoint> endpoints = endpointRepository.findByMerchantIdAndStatus(merchantId, EndpointStatus.ACTIVE);
        List<WebhookEndpoint> subscribed = new ArrayList<>();
        for (WebhookEndpoint ep : endpoints) {
            if (ep.isSubscribedTo(eventType)) {
                subscribed.add(ep);
            }
        }

        if (subscribed.isEmpty()) {
            log.debug("No active subscribed webhook endpoints for merchant [{}] and event [{}]", merchantId, eventType);
            return List.of();
        }

        String serializedPayload;
        try {
            serializedPayload = objectMapper.writeValueAsString(domainEvent);
        } catch (Exception e) {
            log.error("Failed to serialize webhook event payload: {}", e.getMessage());
            return List.of();
        }

        // Persist immutable WebhookEvent
        WebhookEvent webhookEvent = new WebhookEvent(
                domainEvent.getEventId(),
                merchantId,
                eventType,
                domainEvent.getAggregateId(),
                serializedPayload
        );
        webhookEvent = eventRepository.save(webhookEvent);

        List<WebhookDeliveryAttempt> initiatedAttempts = new ArrayList<>();

        for (WebhookEndpoint endpoint : subscribed) {
            String attemptRef = "deliv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            WebhookDeliveryAttempt attempt = new WebhookDeliveryAttempt(
                    attemptRef,
                    webhookEvent,
                    endpoint,
                    merchantId,
                    1,
                    endpoint.getUrl()
            );
            attempt = deliveryAttemptRepository.save(attempt);

            // Execute delivery immediately
            deliveryWorker.executeDelivery(attempt);
            initiatedAttempts.add(attempt);
        }

        log.info("Dispatched domain event [id={}, type={}] to {} merchant webhook endpoints",
                domainEvent.getEventId(), eventType, subscribed.size());

        return initiatedAttempts;
    }
}
