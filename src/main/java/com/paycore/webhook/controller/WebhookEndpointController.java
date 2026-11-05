package com.paycore.webhook.controller;

import com.paycore.common.response.ApiResponse;
import com.paycore.common.response.PagedResponse;
import com.paycore.security.service.SecurityUserPrincipal;
import com.paycore.webhook.dto.WebhookEndpointCreateRequest;
import com.paycore.webhook.dto.WebhookEndpointResponseDto;
import com.paycore.webhook.dto.WebhookEndpointUpdateRequest;
import com.paycore.webhook.entity.EndpointStatus;
import com.paycore.webhook.service.WebhookEndpointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/webhooks/endpoints")
@Tag(name = "Webhook Endpoints", description = "Merchant webhook destination registration and secret rotation")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
public class WebhookEndpointController {

    private final WebhookEndpointService endpointService;

    public WebhookEndpointController(WebhookEndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @PostMapping
    @Operation(summary = "Register Webhook Endpoint", description = "Registers a new webhook destination URL with SSRF validation and returns the signing secret once.")
    public ResponseEntity<ApiResponse<WebhookEndpointResponseDto>> createEndpoint(
            @Valid @RequestBody WebhookEndpointCreateRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        WebhookEndpointResponseDto response = endpointService.createEndpoint(principal.getMerchantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Webhook endpoint registered successfully. Save the signing secret securely now; it will not be displayed again.", response));
    }

    @GetMapping
    @Operation(summary = "List Webhook Endpoints", description = "Retrieves all webhook endpoints configured for the authenticated merchant (never reveals secrets).")
    public ResponseEntity<ApiResponse<List<WebhookEndpointResponseDto>>> listEndpoints(
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        List<WebhookEndpointResponseDto> endpoints = endpointService.listEndpoints(principal.getMerchantId());
        return ResponseEntity.ok(ApiResponse.success("Webhook endpoints retrieved successfully", endpoints));
    }

    @GetMapping("/paged")
    @Operation(summary = "List Webhook Endpoints (Paged)", description = "Retrieves a paginated list of webhook endpoints for the authenticated merchant.")
    public ResponseEntity<ApiResponse<PagedResponse<WebhookEndpointResponseDto>>> listEndpointsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        Page<WebhookEndpointResponseDto> paged = endpointService.listEndpointsPaged(
                principal.getMerchantId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return ResponseEntity.ok(ApiResponse.success("Webhook endpoints retrieved successfully", PagedResponse.from(paged)));
    }

    @GetMapping("/{endpointReference}")
    @Operation(summary = "Get Webhook Endpoint Details", description = "Retrieves metadata for a specific webhook endpoint.")
    public ResponseEntity<ApiResponse<WebhookEndpointResponseDto>> getEndpoint(
            @PathVariable String endpointReference,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        WebhookEndpointResponseDto endpoint = endpointService.getEndpoint(endpointReference, principal.getMerchantId());
        return ResponseEntity.ok(ApiResponse.success("Webhook endpoint retrieved", endpoint));
    }

    @PutMapping("/{endpointReference}")
    @Operation(summary = "Update Webhook Endpoint", description = "Updates URL, subscribed events, or description.")
    public ResponseEntity<ApiResponse<WebhookEndpointResponseDto>> updateEndpoint(
            @PathVariable String endpointReference,
            @Valid @RequestBody WebhookEndpointUpdateRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        WebhookEndpointResponseDto updated = endpointService.updateEndpoint(endpointReference, principal.getMerchantId(), request);
        return ResponseEntity.ok(ApiResponse.success("Webhook endpoint updated successfully", updated));
    }

    @PatchMapping("/{endpointReference}/status")
    @Operation(summary = "Toggle Endpoint Status", description = "Activates or deactivates a webhook endpoint.")
    public ResponseEntity<ApiResponse<WebhookEndpointResponseDto>> toggleStatus(
            @PathVariable String endpointReference,
            @RequestParam EndpointStatus status,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        WebhookEndpointUpdateRequest updateRequest = new WebhookEndpointUpdateRequest();
        updateRequest.setStatus(status);
        WebhookEndpointResponseDto updated = endpointService.updateEndpoint(endpointReference, principal.getMerchantId(), updateRequest);
        return ResponseEntity.ok(ApiResponse.success("Endpoint status updated to " + status, updated));
    }

    @PostMapping("/{endpointReference}/rotate-secret")
    @Operation(summary = "Rotate Signing Secret", description = "Generates a new HMAC signing secret for the endpoint and returns it once.")
    public ResponseEntity<ApiResponse<WebhookEndpointResponseDto>> rotateSecret(
            @PathVariable String endpointReference,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        WebhookEndpointResponseDto rotated = endpointService.rotateSecret(endpointReference, principal.getMerchantId());
        return ResponseEntity.ok(ApiResponse.success("Signing secret rotated successfully. Save the new secret securely now; it will not be displayed again.", rotated));
    }

    @DeleteMapping("/{endpointReference}")
    @Operation(summary = "Delete Webhook Endpoint", description = "Deletes a webhook destination endpoint.")
    public ResponseEntity<ApiResponse<Void>> deleteEndpoint(
            @PathVariable String endpointReference,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        endpointService.deleteEndpoint(endpointReference, principal.getMerchantId());
        return ResponseEntity.ok(ApiResponse.success("Webhook endpoint deleted successfully", null));
    }
}
