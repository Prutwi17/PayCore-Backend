package com.paycore.webhook.controller;

import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.common.response.ApiResponse;
import com.paycore.common.response.PagedResponse;
import com.paycore.event.inbox.InboxEvent;
import com.paycore.event.inbox.InboxEventRepository;
import com.paycore.event.inbox.InboxEventResponseDto;
import com.paycore.event.inbox.InboxStatus;
import com.paycore.event.outbox.*;
import com.paycore.webhook.delivery.WebhookDeliveryWorker;
import com.paycore.webhook.dto.WebhookDeliveryAttemptResponseDto;
import com.paycore.webhook.entity.WebhookDeliveryAttempt;
import com.paycore.webhook.entity.WebhookDeliveryStatus;
import com.paycore.webhook.repository.WebhookDeliveryAttemptRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/webhooks")
@Tag(name = "Admin Webhooks & Messaging Governance", description = "Administrative monitoring of Transactional Outbox, Kafka Inbox, and Webhook Deliveries")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWebhookController {

    private final OutboxService outboxService;
    private final OutboxPublisherScheduler outboxPublisherScheduler;
    private final InboxEventRepository inboxEventRepository;
    private final WebhookDeliveryAttemptRepository deliveryAttemptRepository;
    private final WebhookDeliveryWorker deliveryWorker;

    public AdminWebhookController(OutboxService outboxService,
                                  OutboxPublisherScheduler outboxPublisherScheduler,
                                  InboxEventRepository inboxEventRepository,
                                  WebhookDeliveryAttemptRepository deliveryAttemptRepository,
                                  WebhookDeliveryWorker deliveryWorker) {
        this.outboxService = outboxService;
        this.outboxPublisherScheduler = outboxPublisherScheduler;
        this.inboxEventRepository = inboxEventRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryWorker = deliveryWorker;
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get Webhook & Messaging Telemetry", description = "Retrieves aggregate metrics across Outbox, Deliveries, Retries, and DLQ.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("outboxPendingCount", outboxService.countPending());
        metrics.put("outboxFailedCount", outboxService.countFailed());
        metrics.put("deliverySuccessCount", deliveryAttemptRepository.countSuccessfulDeliveries());
        metrics.put("deliveryRetryingCount", deliveryAttemptRepository.countRetryingDeliveries());
        metrics.put("deliveryDeadLetterCount", deliveryAttemptRepository.countDeadLetterDeliveries());
        return ResponseEntity.ok(ApiResponse.success("Webhook metrics retrieved", metrics));
    }

    @GetMapping("/outbox")
    @Operation(summary = "Monitor Outbox Events Queue", description = "Inspects transactional outbox records with status filtering.")
    public ResponseEntity<ApiResponse<PagedResponse<OutboxEventResponseDto>>> listOutboxEvents(
            @RequestParam(required = false) OutboxStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OutboxEvent> outboxPage = outboxService.listOutboxEvents(status, pageRequest);
        Page<OutboxEventResponseDto> dtoPage = outboxPage.map(OutboxEventResponseDto::fromEntity);
        return ResponseEntity.ok(ApiResponse.success("Outbox events retrieved", PagedResponse.from(dtoPage)));
    }

    @PostMapping("/outbox/{eventId}/republish")
    @Operation(summary = "Force Republish Outbox Event", description = "Forces immediate publishing of a failed or pending outbox event to Kafka.")
    public ResponseEntity<ApiResponse<Boolean>> republishOutboxEvent(@PathVariable String eventId) {
        OutboxEvent event = outboxService.getByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Outbox event not found: " + eventId));

        boolean published = outboxPublisherScheduler.publishSingleEvent(event);
        return ResponseEntity.ok(ApiResponse.success(
                published ? "Event published to Kafka successfully" : "Publishing attempt failed. Scheduled for retry.",
                published
        ));
    }

    @GetMapping("/inbox")
    @Operation(summary = "Monitor Inbox Events & Deduplication", description = "Inspects consumer inbox records and duplicate detection telemetry.")
    public ResponseEntity<ApiResponse<PagedResponse<InboxEventResponseDto>>> listInboxEvents(
            @RequestParam(required = false) InboxStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<InboxEvent> inboxPage = status != null
                ? inboxEventRepository.findByProcessingStatus(status, pageRequest)
                : inboxEventRepository.findAll(pageRequest);

        Page<InboxEventResponseDto> dtoPage = inboxPage.map(InboxEventResponseDto::fromEntity);
        return ResponseEntity.ok(ApiResponse.success("Inbox events retrieved", PagedResponse.from(dtoPage)));
    }

    @GetMapping("/deliveries")
    @Operation(summary = "Global Webhook Deliveries Explorer", description = "Monitors platform-wide webhook deliveries with status filter.")
    public ResponseEntity<ApiResponse<PagedResponse<WebhookDeliveryAttemptResponseDto>>> listAllDeliveries(
            @RequestParam(required = false) WebhookDeliveryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "attemptedAt"));
        Page<WebhookDeliveryAttempt> pageResult = status != null
                ? deliveryAttemptRepository.findByDeliveryStatus(status, pageRequest)
                : deliveryAttemptRepository.findAll(pageRequest);

        Page<WebhookDeliveryAttemptResponseDto> dtoPage = pageResult.map(WebhookDeliveryAttemptResponseDto::fromEntity);
        return ResponseEntity.ok(ApiResponse.success("Global webhook deliveries retrieved", PagedResponse.from(dtoPage)));
    }

    @GetMapping("/dead-letter")
    @Operation(summary = "Dead-Letter Webhook Deliveries", description = "Inspects all exhausted webhook delivery attempts.")
    public ResponseEntity<ApiResponse<PagedResponse<WebhookDeliveryAttemptResponseDto>>> listDeadLetterDeliveries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "attemptedAt"));
        Page<WebhookDeliveryAttempt> pageResult = deliveryAttemptRepository.findByDeliveryStatus(WebhookDeliveryStatus.DEAD_LETTER, pageRequest);
        Page<WebhookDeliveryAttemptResponseDto> dtoPage = pageResult.map(WebhookDeliveryAttemptResponseDto::fromEntity);
        return ResponseEntity.ok(ApiResponse.success("Dead-letter webhook deliveries retrieved", PagedResponse.from(dtoPage)));
    }

    @PostMapping("/deliveries/{attemptReference}/replay")
    @Operation(summary = "Admin Replay Webhook Delivery", description = "Forces an administrative replay attempt for an exhausted delivery.")
    public ResponseEntity<ApiResponse<WebhookDeliveryAttemptResponseDto>> replayDelivery(@PathVariable String attemptReference) {
        WebhookDeliveryAttempt attempt = deliveryAttemptRepository.findByAttemptReference(attemptReference)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook delivery attempt not found: " + attemptReference));

        attempt.setAttemptNumber(attempt.getAttemptNumber() + 1);
        WebhookDeliveryAttempt executed = deliveryWorker.executeDelivery(attempt);

        return ResponseEntity.ok(ApiResponse.success("Webhook delivery replayed by admin", WebhookDeliveryAttemptResponseDto.fromEntity(executed)));
    }
}
