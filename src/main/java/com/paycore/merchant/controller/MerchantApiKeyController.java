package com.paycore.merchant.controller;

import com.paycore.common.response.ApiResponse;
import com.paycore.merchant.dto.ApiKeyCreateRequest;
import com.paycore.merchant.dto.ApiKeyCreatedResponseDto;
import com.paycore.merchant.dto.ApiKeyResponseDto;
import com.paycore.merchant.service.MerchantApiKeyService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/merchants/api-keys")
@Tag(name = "Merchant API Keys", description = "Endpoints for merchant API credential generation, metadata listing, and revocation")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
public class MerchantApiKeyController {

    private final MerchantApiKeyService apiKeyService;

    public MerchantApiKeyController(MerchantApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @Operation(
            summary = "Generate API Key",
            description = "Creates a new secret API key for the merchant. The raw secret key is displayed strictly once upon generation."
    )
    public ResponseEntity<ApiResponse<ApiKeyCreatedResponseDto>> generateApiKey(
            @Valid @RequestBody ApiKeyCreateRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        ApiKeyCreatedResponseDto apiKey = apiKeyService.generateApiKey(principal.getMerchantId(), request);
        return new ResponseEntity<>(ApiResponse.created("API key generated successfully", apiKey), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(
            summary = "List API Keys",
            description = "Retrieves all API keys and their status for the authenticated merchant. Does not return secret keys or hashes."
    )
    public ResponseEntity<ApiResponse<List<ApiKeyResponseDto>>> listApiKeys(
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        List<ApiKeyResponseDto> keys = apiKeyService.listApiKeys(principal.getMerchantId());
        return ResponseEntity.ok(ApiResponse.success("API keys retrieved", keys));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Revoke API Key",
            description = "Revokes an API key belonging to the authenticated merchant. Revoked keys cannot be reactivated."
    )
    public ResponseEntity<ApiResponse<ApiKeyResponseDto>> revokeApiKey(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        ApiKeyResponseDto revoked = apiKeyService.revokeApiKey(id, principal.getMerchantId());
        return ResponseEntity.ok(ApiResponse.success("API key revoked successfully", revoked));
    }
}
