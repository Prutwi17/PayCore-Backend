package com.paycore.webhook.controller;

import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.common.response.ApiResponse;
import com.paycore.common.response.PagedResponse;
import com.paycore.security.service.SecurityUserPrincipal;
import com.paycore.webhook.delivery.WebhookDeliveryWorker;
import com.paycore.webhook.dto.WebhookDeliveryAttemptResponseDto;
import com.paycore.webhook.dto.WebhookEventResponseDto;
import com.paycore.webhook.entity.WebhookDeliveryAttempt;
import com.paycore.webhook.entity.WebhookDeliveryStatus;
import com.paycore.webhook.entity.WebhookEvent;
import com.paycore.webhook.repository.WebhookDeliveryAttemptRepository;
import com.paycore.webhook.repository.WebhookEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhook Deliveries", description = "Merchant webhook event logs, delivery telemetry, and manual retry triggers")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
public class WebhookDeliveryController {

    private final WebhookEventRepository eventRepository;
    private final WebhookDeliveryAttemptRepository deliveryAttemptRepository;
    private final WebhookDeliveryWorker deliveryWorker;

    public WebhookDeliveryController(WebhookEventRepository eventRepository,
                                     WebhookDeliveryAttemptRepository deliveryAttemptRepository,
                                     WebhookDeliveryWorker deliveryWorker) {
        this.eventRepository = eventRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryWorker = deliveryWorker;
    }

    @GetMapping("/events")
    @Operation(summary = "List Webhook Events", description = "Retrieves a paginated list of domain events dispatched to webhooks for this merchant.")
    public ResponseEntity<ApiResponse<PagedResponse<WebhookEventResponseDto>>> listEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WebhookEvent> events;

        if (eventType != null && !eventType.isBlank()) {
            events = eventRepository.findByMerchantIdAndEventType(principal.getMerchantId(), eventType.trim(), pageRequest);
        } else {
            events = eventRepository.findByMerchantId(principal.getMerchantId(), pageRequest);
        }

        Page<WebhookEventResponseDto> dtoPage = events.map(WebhookEventResponseDto::fromEntity);
        return ResponseEntity.ok(ApiResponse.success("Webhook events retrieved successfully", PagedResponse.from(dtoPage)));
    }

    @GetMapping("/deliveries")
    @Operation(summary = "List Webhook Delivery Attempts", description = "Retrieves delivery attempts for this merchant with status filtering.")
    public ResponseEntity<ApiResponse<PagedResponse<WebhookDeliveryAttemptResponseDto>>> listDeliveries(
            @RequestParam(required = false) WebhookDeliveryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "attemptedAt"));
        Page<WebhookDeliveryAttempt> attempts;

        if (status != null) {
            attempts = deliveryAttemptRepository.findByMerchantIdAndDeliveryStatus(principal.getMerchantId(), status, pageRequest);
        } else {
            attempts = deliveryAttemptRepository.findByMerchantId(principal.getMerchantId(), pageRequest);
        }

        Page<WebhookDeliveryAttemptResponseDto> dtoPage = attempts.map(WebhookDeliveryAttemptResponseDto::fromEntity);
        return ResponseEntity.ok(ApiResponse.success("Webhook delivery attempts retrieved successfully", PagedResponse.from(dtoPage)));
    }

    @GetMapping("/deliveries/{attemptReference}")
    @Operation(summary = "Get Delivery Attempt Details", description = "Retrieves full request headers, payload, response snippet, and duration for an attempt.")
    public ResponseEntity<ApiResponse<WebhookDeliveryAttemptResponseDto>> getDeliveryDetails(
            @PathVariable String attemptReference,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        WebhookDeliveryAttempt attempt = deliveryAttemptRepository.findByAttemptReferenceAndMerchantId(attemptReference, principal.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Webhook delivery attempt not found: " + attemptReference));

        return ResponseEntity.ok(ApiResponse.success("Delivery attempt details retrieved", WebhookDeliveryAttemptResponseDto.fromEntity(attempt)));
    }

    @PostMapping("/deliveries/{attemptReference}/retry")
    @Operation(summary = "Retry Webhook Delivery", description = "Manually triggers an immediate retry for a failed or dead-letter delivery attempt.")
    public ResponseEntity<ApiResponse<WebhookDeliveryAttemptResponseDto>> retryDelivery(
            @PathVariable String attemptReference,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        WebhookDeliveryAttempt attempt = deliveryAttemptRepository.findByAttemptReferenceAndMerchantId(attemptReference, principal.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Webhook delivery attempt not found: " + attemptReference));

        attempt.setAttemptNumber(attempt.getAttemptNumber() + 1);
        WebhookDeliveryAttempt executed = deliveryWorker.executeDelivery(attempt);

        return ResponseEntity.ok(ApiResponse.success("Webhook delivery retried", WebhookDeliveryAttemptResponseDto.fromEntity(executed)));
    }
}
