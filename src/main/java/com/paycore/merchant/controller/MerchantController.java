package com.paycore.merchant.controller;

import com.paycore.common.response.ApiResponse;
import com.paycore.merchant.dto.MerchantRegistrationRequest;
import com.paycore.merchant.dto.MerchantResponseDto;
import com.paycore.merchant.dto.MerchantUpdateRequest;
import com.paycore.merchant.service.MerchantService;
import com.paycore.security.service.SecurityUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchants")
@Tag(name = "Merchant Operations", description = "Endpoints for merchant onboarding, profile inspection, and settings")
@SecurityRequirement(name = "BearerAuth")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping
    @Operation(summary = "Register merchant account", description = "Onboards a new merchant tenancy linked to the authenticated user.")
    public ResponseEntity<ApiResponse<MerchantResponseDto>> createMerchant(
            @Valid @RequestBody MerchantRegistrationRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        MerchantResponseDto merchant = merchantService.createMerchant(request, principal.getId());
        return new ResponseEntity<>(ApiResponse.created("Merchant account registered", merchant), HttpStatus.CREATED);
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Get current merchant profile", description = "Fetches the profile for the authenticated merchant context.")
    public ResponseEntity<ApiResponse<MerchantResponseDto>> getProfile(
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        MerchantResponseDto merchant = merchantService.getMerchantProfile(principal.getMerchantId());
        return ResponseEntity.ok(ApiResponse.success("Merchant profile retrieved", merchant));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Update current merchant profile", description = "Updates business details for the authenticated merchant.")
    public ResponseEntity<ApiResponse<MerchantResponseDto>> updateProfile(
            @Valid @RequestBody MerchantUpdateRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        MerchantResponseDto merchant = merchantService.updateMerchantProfile(principal.getMerchantId(), request);
        return ResponseEntity.ok(ApiResponse.success("Merchant profile updated successfully", merchant));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Get merchant by ID", description = "Retrieves merchant details. Enforces strict tenant ownership check.")
    public ResponseEntity<ApiResponse<MerchantResponseDto>> getMerchantById(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        MerchantResponseDto merchant = merchantService.getMerchantById(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Merchant details retrieved", merchant));
    }
}
