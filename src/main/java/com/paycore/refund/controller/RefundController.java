package com.paycore.refund.controller;

import com.paycore.common.response.ApiResponse;
import com.paycore.common.response.PagedResponse;
import com.paycore.refund.dto.RefundCreateRequest;
import com.paycore.refund.dto.RefundResponseDto;
import com.paycore.refund.dto.RefundableAmountDto;
import com.paycore.refund.entity.RefundStatus;
import com.paycore.refund.service.RefundService;
import com.paycore.security.service.SecurityUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1")
@Tag(name = "Refund Management", description = "Refund initiation, tracking, and balance verification APIs")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/payments/{paymentReference}/refunds")
    @Operation(
            summary = "Initiate Refund with Idempotency",
            description = "Initiates a full or partial refund for an eligible payment. Supports an optional Idempotency-Key header to prevent duplicate charges."
    )
    public ResponseEntity<ApiResponse<RefundResponseDto>> initiateRefund(
            @PathVariable String paymentReference,
            @Parameter(description = "Optional unique client-generated idempotency key (1-128 chars)", example = "ref-idemp-12345")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RefundCreateRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        RefundResponseDto response = refundService.createRefund(
                paymentReference, principal.getMerchantId(), idempotencyKey, request
        );

        HttpStatus status = response.isIdempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        String message = response.isIdempotentReplay()
                ? "Idempotent refund response replayed"
                : "Refund processed successfully";

        return ResponseEntity.status(status).body(ApiResponse.success(message, response));
    }

    @GetMapping("/refunds")
    @Operation(
            summary = "List Merchant Refunds",
            description = "Retrieves a paginated list of refunds initiated by the authenticated merchant."
    )
    public ResponseEntity<ApiResponse<PagedResponse<RefundResponseDto>>> listRefunds(
            @RequestParam(required = false) RefundStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        Sort sort = sortDirection.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<RefundResponseDto> refundsPage = refundService.searchMerchantRefunds(
                principal.getMerchantId(), status, search, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Refunds retrieved successfully", PagedResponse.from(refundsPage)));
    }

    @GetMapping("/refunds/{refundReference}")
    @Operation(
            summary = "Get Refund Details",
            description = "Retrieves full details for a refund by unique refund reference."
    )
    public ResponseEntity<ApiResponse<RefundResponseDto>> getRefund(
            @PathVariable String refundReference,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        RefundResponseDto refund = refundService.getRefundByReference(
                refundReference, principal.getMerchantId(), principal.hasRole("ROLE_ADMIN")
        );
        return ResponseEntity.ok(ApiResponse.success("Refund retrieved", refund));
    }

    @GetMapping("/payments/{paymentReference}/refunds")
    @Operation(
            summary = "List Payment Refunds",
            description = "Retrieves all refunds that have been issued for a specific payment."
    )
    public ResponseEntity<ApiResponse<List<RefundResponseDto>>> getPaymentRefunds(
            @PathVariable String paymentReference,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        List<RefundResponseDto> refunds = refundService.getRefundsForPayment(
                paymentReference, principal.getMerchantId(), principal.hasRole("ROLE_ADMIN")
        );
        return ResponseEntity.ok(ApiResponse.success("Payment refunds retrieved", refunds));
    }

    @GetMapping("/payments/{paymentReference}/refundable-amount")
    @Operation(
            summary = "Get Refundable Amount",
            description = "Calculates the live remaining refundable balance, refunded totals, and refund eligibility for a payment."
    )
    public ResponseEntity<ApiResponse<RefundableAmountDto>> getRefundableAmount(
            @PathVariable String paymentReference,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        RefundableAmountDto refundable = refundService.getRefundableAmount(
                paymentReference, principal.getMerchantId(), principal.hasRole("ROLE_ADMIN")
        );
        return ResponseEntity.ok(ApiResponse.success("Refundable amount calculated", refundable));
    }

    @PostMapping("/refunds/{refundReference}/verify")
    @Operation(
            summary = "Verify UNKNOWN Refund Status",
            description = "Queries downstream payment provider for status reconciliation on an UNKNOWN refund outcome."
    )
    public ResponseEntity<ApiResponse<RefundResponseDto>> verifyRefund(
            @PathVariable String refundReference,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        RefundResponseDto verified = refundService.verifyRefundStatus(
                refundReference, principal.getMerchantId(), principal.hasRole("ROLE_ADMIN")
        );
        return ResponseEntity.ok(ApiResponse.success("Refund status verified", verified));
    }
}
