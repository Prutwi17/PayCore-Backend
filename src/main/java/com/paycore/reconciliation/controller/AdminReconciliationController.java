package com.paycore.reconciliation.controller;

import com.paycore.common.response.ApiResponse;
import com.paycore.reconciliation.dto.DiscrepancyResolutionRequest;
import com.paycore.reconciliation.dto.ReconciliationRecordResponseDto;
import com.paycore.reconciliation.dto.ReconciliationRunRequest;
import com.paycore.reconciliation.dto.ReconciliationRunResponseDto;
import com.paycore.reconciliation.entity.DiscrepancyType;
import com.paycore.reconciliation.entity.RecordStatus;
import com.paycore.reconciliation.entity.RecordType;
import com.paycore.reconciliation.entity.RunStatus;
import com.paycore.reconciliation.service.ReconciliationService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reconciliation")
@Tag(name = "Admin Reconciliation", description = "Manage payment and refund settlement reconciliation runs and discrepancy resolution")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReconciliationController {

    private final ReconciliationService reconciliationService;

    public AdminReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/runs")
    @Operation(summary = "Initiate a manual or scheduled reconciliation run")
    public ResponseEntity<ApiResponse<ReconciliationRunResponseDto>> initiateRun(
            @Valid @RequestBody ReconciliationRunRequest request,
            Authentication authentication
    ) {
        String adminUser = authentication != null ? authentication.getName() : "ADMIN";
        ReconciliationRunResponseDto response = reconciliationService.initiateReconciliationRun(request, adminUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reconciliation run completed", response));
    }

    @GetMapping("/runs")
    @Operation(summary = "List reconciliation run history with pagination and status filtering")
    public ResponseEntity<ApiResponse<Page<ReconciliationRunResponseDto>>> getRuns(
            @RequestParam(required = false) RunStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReconciliationRunResponseDto> runs = reconciliationService.getRuns(status, pageRequest);
        return ResponseEntity.ok(ApiResponse.success("Reconciliation runs retrieved", runs));
    }

    @GetMapping("/runs/{runReference}")
    @Operation(summary = "Get reconciliation run summary by run reference")
    public ResponseEntity<ApiResponse<ReconciliationRunResponseDto>> getRunByReference(
            @PathVariable String runReference
    ) {
        ReconciliationRunResponseDto run = reconciliationService.getRunByReference(runReference);
        return ResponseEntity.ok(ApiResponse.success("Reconciliation run retrieved", run));
    }

    @GetMapping("/runs/{runReference}/records")
    @Operation(summary = "Get matched and discrepancy records for a specific run")
    public ResponseEntity<ApiResponse<Page<ReconciliationRecordResponseDto>>> getRecordsByRun(
            @PathVariable String runReference,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) DiscrepancyType discrepancyType,
            @RequestParam(required = false) RecordType recordType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));
        Page<ReconciliationRecordResponseDto> records = reconciliationService.getRecordsByRun(
                runReference, status, discrepancyType, recordType, pageRequest
        );
        return ResponseEntity.ok(ApiResponse.success("Reconciliation records retrieved", records));
    }

    @GetMapping("/discrepancies")
    @Operation(summary = "Explore all discrepancies across reconciliation runs")
    public ResponseEntity<ApiResponse<Page<ReconciliationRecordResponseDto>>> getDiscrepancies(
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) DiscrepancyType discrepancyType,
            @RequestParam(required = false) RecordType recordType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReconciliationRecordResponseDto> discrepancies = reconciliationService.getDiscrepancies(
                status, discrepancyType, recordType, pageRequest
        );
        return ResponseEntity.ok(ApiResponse.success("Discrepancies retrieved", discrepancies));
    }

    @GetMapping("/discrepancies/{id}")
    @Operation(summary = "Get specific discrepancy detail by record ID")
    public ResponseEntity<ApiResponse<ReconciliationRecordResponseDto>> getDiscrepancyById(
            @PathVariable Long id
    ) {
        ReconciliationRecordResponseDto dto = reconciliationService.getDiscrepancyById(id);
        return ResponseEntity.ok(ApiResponse.success("Discrepancy retrieved", dto));
    }

    @PostMapping("/discrepancies/{id}/resolve")
    @Operation(summary = "Record administrative resolution for a detected settlement discrepancy")
    public ResponseEntity<ApiResponse<ReconciliationRecordResponseDto>> resolveDiscrepancy(
            @PathVariable Long id,
            @Valid @RequestBody DiscrepancyResolutionRequest request,
            Authentication authentication
    ) {
        String adminUser = authentication != null ? authentication.getName() : "ADMIN";
        ReconciliationRecordResponseDto resolved = reconciliationService.resolveDiscrepancy(id, request, adminUser);
        return ResponseEntity.ok(ApiResponse.success("Discrepancy resolved successfully", resolved));
    }
}
