package com.paycore.merchant.controller;

import com.paycore.common.response.ApiResponse;
import com.paycore.common.response.PagedResponse;
import com.paycore.merchant.dto.MerchantResponseDto;
import com.paycore.merchant.dto.MerchantStatusUpdateRequest;
import com.paycore.merchant.entity.MerchantStatus;
import com.paycore.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/merchants")
@Tag(name = "Admin Merchant Management", description = "Administrative endpoints for merchant review, approval, suspension, and auditing")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMerchantController {

    private final MerchantService merchantService;

    public AdminMerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    @Operation(summary = "List all merchants", description = "Retrieves paginated list of merchants with optional status filtering. ADMIN only.")
    public ResponseEntity<ApiResponse<PagedResponse<MerchantResponseDto>>> listMerchants(
            @RequestParam(required = false) MerchantStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<MerchantResponseDto> merchantPage = merchantService.listMerchants(status, pageable);

        PagedResponse<MerchantResponseDto> paged = new PagedResponse<>(
                merchantPage.getContent(),
                merchantPage.getNumber(),
                merchantPage.getSize(),
                merchantPage.getTotalElements(),
                merchantPage.getTotalPages(),
                merchantPage.isLast()
        );

        return ResponseEntity.ok(ApiResponse.success("Merchants retrieved", paged));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update merchant status", description = "Updates a merchant's status (ACTIVE, SUSPENDED, REJECTED). ADMIN only.")
    public ResponseEntity<ApiResponse<MerchantResponseDto>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody MerchantStatusUpdateRequest request
    ) {
        MerchantResponseDto updated = merchantService.updateMerchantStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Merchant status updated to " + request.getStatus(), updated));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Approve merchant", description = "Shortcut action to approve and activate a pending merchant. ADMIN only.")
    public ResponseEntity<ApiResponse<MerchantResponseDto>> approveMerchant(@PathVariable Long id) {
        MerchantResponseDto updated = merchantService.updateMerchantStatus(id, MerchantStatus.ACTIVE);
        return ResponseEntity.ok(ApiResponse.success("Merchant approved successfully", updated));
    }

    @PatchMapping("/{id}/suspend")
    @Operation(summary = "Suspend merchant", description = "Shortcut action to suspend a merchant. ADMIN only.")
    public ResponseEntity<ApiResponse<MerchantResponseDto>> suspendMerchant(@PathVariable Long id) {
        MerchantResponseDto updated = merchantService.updateMerchantStatus(id, MerchantStatus.SUSPENDED);
        return ResponseEntity.ok(ApiResponse.success("Merchant suspended successfully", updated));
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Reject merchant", description = "Shortcut action to reject a merchant application. ADMIN only.")
    public ResponseEntity<ApiResponse<MerchantResponseDto>> rejectMerchant(@PathVariable Long id) {
        MerchantResponseDto updated = merchantService.updateMerchantStatus(id, MerchantStatus.REJECTED);
        return ResponseEntity.ok(ApiResponse.success("Merchant rejected successfully", updated));
    }
}
