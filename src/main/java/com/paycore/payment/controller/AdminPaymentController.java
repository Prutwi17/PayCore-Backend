package com.paycore.payment.controller;

import com.paycore.common.response.ApiResponse;
import com.paycore.common.response.PagedResponse;
import com.paycore.payment.dto.PaymentAttemptResponseDto;
import com.paycore.payment.dto.PaymentResponseDto;
import com.paycore.payment.entity.PaymentStatus;
import com.paycore.payment.service.PaymentService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/payments")
@Tag(name = "Admin Payments", description = "System-wide payment monitoring and administrative auditing")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    @Operation(
            summary = "List All System Payments",
            description = "Allows platform administrators to search and audit payments across all merchants."
    )
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponseDto>>> listAllPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        Sort sort = sortDirection.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<PaymentResponseDto> paymentsPage = paymentService.listAdminPayments(status, merchantId, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("System payments retrieved", PagedResponse.from(paymentsPage)));
    }

    @GetMapping("/{paymentReference}")
    @Operation(
            summary = "Admin Get Payment Details",
            description = "Fetches complete payment details across any merchant organization."
    )
    public ResponseEntity<ApiResponse<PaymentResponseDto>> getPaymentDetails(
            @PathVariable String paymentReference
    ) {
        PaymentResponseDto payment = paymentService.getPaymentByReference(paymentReference, null, true);
        return ResponseEntity.ok(ApiResponse.success("Payment retrieved", payment));
    }

    @GetMapping("/{paymentReference}/attempts")
    @Operation(
            summary = "Admin Get Payment Attempts",
            description = "Fetches attempt history for any payment across the platform."
    )
    public ResponseEntity<ApiResponse<List<PaymentAttemptResponseDto>>> getPaymentAttempts(
            @PathVariable String paymentReference
    ) {
        List<PaymentAttemptResponseDto> attempts = paymentService.getPaymentAttempts(paymentReference, null, true);
        return ResponseEntity.ok(ApiResponse.success("Payment attempts retrieved", attempts));
    }
}
