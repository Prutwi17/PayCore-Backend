package com.paycore.dashboard.controller;

import com.paycore.common.response.ApiResponse;
import com.paycore.dashboard.dto.MerchantDashboardSummaryDto;
import com.paycore.dashboard.service.MerchantDashboardService;
import com.paycore.merchant.repository.MerchantRepository;
import com.paycore.security.service.SecurityUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchant/dashboard")
@Tag(name = "Merchant Dashboard & Analytics", description = "Real-time metrics, payment stats, refund totals, and recent transaction telemetry for merchants")
@SecurityRequirement(name = "BearerAuth")
public class MerchantDashboardController {

    private final MerchantDashboardService merchantDashboardService;
    private final MerchantRepository merchantRepository;

    public MerchantDashboardController(
            MerchantDashboardService merchantDashboardService,
            MerchantRepository merchantRepository
    ) {
        this.merchantDashboardService = merchantDashboardService;
        this.merchantRepository = merchantRepository;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get merchant-specific summary KPIs and recent activity")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<MerchantDashboardSummaryDto>> getSummary(
            @AuthenticationPrincipal SecurityUserPrincipal principal,
            @RequestParam(required = false) Long merchantId
    ) {
        Long targetMerchantId = principal.getMerchantId();

        // If admin requests summary without assigned merchantId, allow query param or fallback to first available merchant
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin && merchantId != null) {
            targetMerchantId = merchantId;
        } else if (isAdmin && targetMerchantId == null) {
            targetMerchantId = merchantRepository.findAll().stream()
                    .findFirst()
                    .map(com.paycore.merchant.entity.Merchant::getId)
                    .orElse(null);
        }

        MerchantDashboardSummaryDto summary = merchantDashboardService.getMerchantSummary(targetMerchantId);
        return ResponseEntity.ok(ApiResponse.success("Merchant dashboard summary retrieved", summary));
    }
}
