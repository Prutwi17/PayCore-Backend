package com.paycore.dashboard.controller;

import com.paycore.common.response.ApiResponse;
import com.paycore.dashboard.dto.*;
import com.paycore.dashboard.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@Tag(name = "Admin Analytics & Dashboard", description = "Real-time metrics, volume trends, and status distributions derived from internal database records")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get high-level payment, refund, webhook, and reconciliation KPI metrics")
    public ResponseEntity<ApiResponse<AdminDashboardSummaryDto>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        AdminDashboardSummaryDto summary = dashboardService.getDashboardSummary(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Dashboard summary retrieved", summary));
    }

    @GetMapping("/payment-trends")
    @Operation(summary = "Get daily aggregated payment volumes, success counts, and failure counts")
    public ResponseEntity<ApiResponse<List<PaymentTrendPointDto>>> getPaymentTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        List<PaymentTrendPointDto> trends = dashboardService.getPaymentTrends(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Payment trends retrieved", trends));
    }

    @GetMapping("/refund-trends")
    @Operation(summary = "Get daily aggregated refund counts and settlement volume")
    public ResponseEntity<ApiResponse<List<RefundTrendPointDto>>> getRefundTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        List<RefundTrendPointDto> trends = dashboardService.getRefundTrends(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Refund trends retrieved", trends));
    }

    @GetMapping("/payment-status-distribution")
    @Operation(summary = "Get payment distribution broken down by lifecycle status")
    public ResponseEntity<ApiResponse<List<StatusDistributionDto>>> getPaymentStatusDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        List<StatusDistributionDto> distribution = dashboardService.getPaymentStatusDistribution(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Payment status distribution retrieved", distribution));
    }

    @GetMapping("/reconciliation-summary")
    @Operation(summary = "Get summary of reconciliation discrepancy counts, active runs, and resolution state")
    public ResponseEntity<ApiResponse<ReconciliationSummaryDto>> getReconciliationSummary() {
        ReconciliationSummaryDto summary = dashboardService.getReconciliationSummary();
        return ResponseEntity.ok(ApiResponse.success("Reconciliation summary retrieved", summary));
    }
}
