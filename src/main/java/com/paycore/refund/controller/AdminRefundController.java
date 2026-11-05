package com.paycore.refund.controller;

import com.paycore.common.response.ApiResponse;
import com.paycore.common.response.PagedResponse;
import com.paycore.refund.dto.RefundResponseDto;
import com.paycore.refund.entity.RefundStatus;
import com.paycore.refund.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/refunds")
@Tag(name = "Admin Refund Management", description = "Administrative refund oversight across merchants")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {

    private final RefundService refundService;

    public AdminRefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    @Operation(
            summary = "List All Refunds (Admin)",
            description = "Retrieves a paginated list of all refunds system-wide with optional merchant and status filters."
    )
    public ResponseEntity<ApiResponse<PagedResponse<RefundResponseDto>>> listAdminRefunds(
            @RequestParam(required = false) RefundStatus status,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        Sort sort = sortDirection.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<RefundResponseDto> refundsPage = refundService.searchAdminRefunds(status, merchantId, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Admin refunds retrieved successfully", PagedResponse.from(refundsPage)));
    }

    @GetMapping("/{refundReference}")
    @Operation(
            summary = "Get Refund Details (Admin)",
            description = "Retrieves detailed refund record across any merchant."
    )
    public ResponseEntity<ApiResponse<RefundResponseDto>> getAdminRefund(
            @PathVariable String refundReference
    ) {
        RefundResponseDto refund = refundService.getRefundByReference(refundReference, null, true);
        return ResponseEntity.ok(ApiResponse.success("Admin refund details retrieved", refund));
    }

    @PostMapping("/{refundReference}/verify")
    @Operation(
            summary = "Reconcile UNKNOWN Refund Status (Admin)",
            description = "Authoritative provider status check to resolve UNKNOWN refund outcome."
    )
    public ResponseEntity<ApiResponse<RefundResponseDto>> verifyAdminRefund(
            @PathVariable String refundReference
    ) {
        RefundResponseDto verified = refundService.verifyRefundStatus(refundReference, null, true);
        return ResponseEntity.ok(ApiResponse.success("Admin refund status verified", verified));
    }
}
