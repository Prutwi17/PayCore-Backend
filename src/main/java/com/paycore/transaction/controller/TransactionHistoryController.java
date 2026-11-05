package com.paycore.transaction.controller;

import com.paycore.common.response.ApiResponse;
import com.paycore.common.response.PagedResponse;
import com.paycore.security.service.SecurityUserPrincipal;
import com.paycore.transaction.dto.TransactionItemDto;
import com.paycore.transaction.dto.TransactionType;
import com.paycore.transaction.service.TransactionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transaction History", description = "Unified ledger of all payments and refunds with database-level pagination")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
public class TransactionHistoryController {

    private final TransactionHistoryService transactionHistoryService;

    public TransactionHistoryController(TransactionHistoryService transactionHistoryService) {
        this.transactionHistoryService = transactionHistoryService;
    }

    @GetMapping
    @Operation(
            summary = "List Merchant Transactions",
            description = "Retrieves a unified, paginated ledger of payments and refunds for the authenticated merchant."
    )
    public ResponseEntity<ApiResponse<PagedResponse<TransactionItemDto>>> listTransactions(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        Sort sort = sortDirection.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<TransactionItemDto> transactions = transactionHistoryService.searchMerchantTransactions(
                principal.getMerchantId(), type, status, search, startDate, endDate, pageable
        );

        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved successfully", PagedResponse.from(transactions)));
    }
}
