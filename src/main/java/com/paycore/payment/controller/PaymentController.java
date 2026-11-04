package com.paycore.payment.controller;

import com.paycore.common.response.ApiResponse;
import com.paycore.common.response.PagedResponse;
import com.paycore.payment.dto.PaymentAttemptResponseDto;
import com.paycore.payment.dto.PaymentCreateRequest;
import com.paycore.payment.dto.PaymentResponseDto;
import com.paycore.payment.dto.PaymentStatusResponseDto;
import com.paycore.payment.entity.PaymentStatus;
import com.paycore.payment.service.PaymentService;
import com.paycore.security.service.SecurityUserPrincipal;
import com.paycore.payment.idempotency.dto.IdempotencyResult;
import com.paycore.payment.idempotency.service.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpHeaders;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payment Processing", description = "Core payment initiation, query, and lifecycle APIs")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    public PaymentController(PaymentService paymentService, IdempotencyService idempotencyService) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    @Operation(
            summary = "Initiate Payment with Idempotency",
            description = "Creates or returns an idempotent payment processing operation. Requires an Idempotency-Key header scoped to the merchant."
    )
    public ResponseEntity<ApiResponse<PaymentResponseDto>> createPayment(
            @Parameter(description = "Unique client-generated idempotency key (1-128 chars)", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentCreateRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        IdempotencyResult result = idempotencyService.processPaymentWithIdempotency(
                principal.getMerchantId(), idempotencyKey, request
        );

        HttpHeaders headers = new HttpHeaders();
        if (result.isReplay()) {
            headers.add("Idempotent-Replay", "true");
            return new ResponseEntity<>(
                    ApiResponse.success("Payment retrieved via idempotent replay", result.getResponse()),
                    headers,
                    HttpStatus.OK
            );
        }

        return new ResponseEntity<>(
                ApiResponse.created("Payment processed successfully", result.getResponse()),
                headers,
                HttpStatus.CREATED
        );
    }

    @GetMapping
    @Operation(
            summary = "List Merchant Payments",
            description = "Retrieves a paginated list of payments initiated by the authenticated merchant."
    )
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponseDto>>> listPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        Sort sort = sortDirection.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<PaymentResponseDto> paymentsPage = paymentService.listMerchantPayments(
                principal.getMerchantId(), status, search, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved successfully", PagedResponse.from(paymentsPage)));
    }

    @GetMapping("/{paymentReference}")
    @Operation(
            summary = "Get Payment Details",
            description = "Fetches complete payment details by unique payment reference, enforcing merchant ownership."
    )
    public ResponseEntity<ApiResponse<PaymentResponseDto>> getPayment(
            @PathVariable String paymentReference,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        PaymentResponseDto payment = paymentService.getPaymentByReference(
                paymentReference, principal.getMerchantId(), principal.hasRole("ROLE_ADMIN")
        );
        return ResponseEntity.ok(ApiResponse.success("Payment retrieved", payment));
    }

    @GetMapping("/{paymentReference}/attempts")
    @Operation(
            summary = "Get Payment Attempts",
            description = "Retrieves the historical provider dispatch attempts and responses for a payment."
    )
    public ResponseEntity<ApiResponse<List<PaymentAttemptResponseDto>>> getPaymentAttempts(
            @PathVariable String paymentReference,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        List<PaymentAttemptResponseDto> attempts = paymentService.getPaymentAttempts(
                paymentReference, principal.getMerchantId(), principal.hasRole("ROLE_ADMIN")
        );
        return ResponseEntity.ok(ApiResponse.success("Payment attempts retrieved", attempts));
    }

    @GetMapping("/{paymentReference}/status")
    @Operation(
            summary = "Query Payment Status",
            description = "Returns current payment status. If status is UNKNOWN, optionally triggers downstream provider verification."
    )
    public ResponseEntity<ApiResponse<PaymentStatusResponseDto>> getPaymentStatus(
            @PathVariable String paymentReference,
            @RequestParam(defaultValue = "true") boolean verify,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        PaymentStatusResponseDto status = paymentService.getPaymentStatus(
                paymentReference, principal.getMerchantId(), principal.hasRole("ROLE_ADMIN"), verify
        );
        return ResponseEntity.ok(ApiResponse.success("Payment status retrieved", status));
    }

    @PostMapping("/{paymentReference}/cancel")
    @Operation(
            summary = "Cancel Payment",
            description = "Cancels an eligible payment (CREATED, REQUIRES_ACTION, AUTHORIZED)."
    )
    public ResponseEntity<ApiResponse<PaymentResponseDto>> cancelPayment(
            @PathVariable String paymentReference,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        PaymentResponseDto cancelled = paymentService.cancelPayment(
                paymentReference, principal.getMerchantId(), principal.hasRole("ROLE_ADMIN"), reason
        );
        return ResponseEntity.ok(ApiResponse.success("Payment cancelled successfully", cancelled));
    }
}
