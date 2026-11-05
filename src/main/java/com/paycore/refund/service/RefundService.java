package com.paycore.refund.service;

import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.payment.entity.Payment;
import com.paycore.payment.exception.PaymentValidationException;
import com.paycore.payment.idempotency.service.IdempotencyService;
import com.paycore.payment.provider.PaymentProvider;
import com.paycore.payment.provider.PaymentProviderRefundRequest;
import com.paycore.payment.provider.PaymentProviderRefundResponse;
import com.paycore.payment.repository.PaymentRepository;
import com.paycore.refund.dto.*;
import com.paycore.refund.entity.Refund;
import com.paycore.refund.entity.RefundStatus;
import com.paycore.refund.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * High-reliability refund orchestration service.
 * Follows the Zero-DB Network I/O Principle and coordinates atomic reservation
 * and idempotency enforcement.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundTransactionHelper refundTransactionHelper;
    private final PaymentProvider paymentProvider;
    private final IdempotencyService idempotencyService;

    public RefundService(
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            RefundTransactionHelper refundTransactionHelper,
            PaymentProvider paymentProvider,
            IdempotencyService idempotencyService
    ) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.refundTransactionHelper = refundTransactionHelper;
        this.paymentProvider = paymentProvider;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Entry point for refund creation. Supports optional Idempotency-Key.
     */
    public RefundResponseDto createRefund(
            String paymentReference,
            Long merchantId,
            String idempotencyKey,
            RefundCreateRequest request
    ) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            RefundIdempotencyResult result = idempotencyService.processRefundWithIdempotency(
                    merchantId, paymentReference, idempotencyKey, request,
                    () -> executeRefund(paymentReference, merchantId, request)
            );
            return result.getResponse();
        }

        return executeRefund(paymentReference, merchantId, request);
    }

    /**
     * Internal two-phase refund execution strictly honoring the Zero-DB Network I/O Principle:
     * Phase 1: Atomically lock, validate, reserve balance, and persist initial record (Short TX).
     * Call downstream provider outside any open database transaction.
     * Phase 2: Lock and commit final state based on provider response (Short TX).
     */
    public RefundResponseDto executeRefund(
            String paymentReference,
            Long merchantId,
            RefundCreateRequest request
    ) {
        // Phase 1: DB Transaction 1 (Reserve balance + create in PROCESSING)
        RefundReservationResult reservation = refundTransactionHelper.reserveAndCreateRefund(
                paymentReference, merchantId, request
        );

        // Outside DB Transaction: Network I/O to provider
        PaymentProviderRefundRequest providerRequest = new PaymentProviderRefundRequest(
                reservation.getRefundReference(),
                reservation.getPaymentReference(),
                reservation.getProviderTransactionId(),
                reservation.getAmount(),
                reservation.getCurrency(),
                reservation.getReason(),
                null
        );

        PaymentProviderRefundResponse providerResponse = null;
        Exception providerError = null;
        try {
            providerResponse = paymentProvider.initiateRefund(providerRequest);
        } catch (Exception ex) {
            log.warn("Downstream payment provider refund call failed/timed out for refund [{}]: {}",
                    reservation.getRefundReference(), ex.getMessage());
            providerError = ex;
        }

        // Phase 2: DB Transaction 2 (Finalize outcome)
        return refundTransactionHelper.finalizeRefund(
                reservation.getRefundId(),
                reservation.getPaymentId(),
                providerResponse,
                providerError
        );
    }

    /**
     * Computes the current refundable balance and status for a payment.
     */
    @Transactional(readOnly = true)
    public RefundableAmountDto getRefundableAmount(String paymentReference, Long merchantId, boolean isAdmin) {
        Payment payment = paymentRepository.findByPaymentReferenceWithMerchant(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with reference: " + paymentReference));

        if (!isAdmin && merchantId != null && !payment.getMerchant().getId().equals(merchantId)) {
            throw new PaymentValidationException("Payment does not belong to the authenticated merchant");
        }

        return new RefundableAmountDto(
                payment.getPaymentReference(),
                payment.getCurrency(),
                payment.getAmount(),
                payment.getAmountRefunded(),
                payment.getAmountRefundReserved(),
                payment.getRemainingRefundableAmount(),
                payment.isRefundable()
        );
    }

    /**
     * Retrieves details for a specific refund by its unique reference.
     */
    @Transactional(readOnly = true)
    public RefundResponseDto getRefundByReference(String refundReference, Long merchantId, boolean isAdmin) {
        Refund refund = refundRepository.findByRefundReferenceWithDetails(refundReference)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found with reference: " + refundReference));

        if (!isAdmin && merchantId != null && !refund.getMerchant().getId().equals(merchantId)) {
            throw new PaymentValidationException("Refund does not belong to the authenticated merchant");
        }

        return RefundResponseDto.fromEntity(refund);
    }

    /**
     * Lists all refund records associated with a given payment reference.
     */
    @Transactional(readOnly = true)
    public List<RefundResponseDto> getRefundsForPayment(String paymentReference, Long merchantId, boolean isAdmin) {
        Payment payment = paymentRepository.findByPaymentReferenceWithMerchant(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with reference: " + paymentReference));

        if (!isAdmin && merchantId != null && !payment.getMerchant().getId().equals(merchantId)) {
            throw new PaymentValidationException("Payment does not belong to the authenticated merchant");
        }

        List<Refund> refunds;
        if (isAdmin || merchantId == null) {
            refunds = refundRepository.findByPaymentReferenceOrderByCreatedAtDesc(paymentReference);
        } else {
            refunds = refundRepository.findByPaymentReferenceAndMerchantIdOrderByCreatedAtDesc(paymentReference, merchantId);
        }

        return refunds.stream()
                .map(RefundResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Paginated search for merchant refunds.
     */
    @Transactional(readOnly = true)
    public Page<RefundResponseDto> searchMerchantRefunds(
            Long merchantId,
            RefundStatus status,
            String search,
            Pageable pageable
    ) {
        return refundRepository.searchMerchantRefunds(merchantId, status, search, pageable)
                .map(RefundResponseDto::fromEntity);
    }

    /**
     * Paginated search for administrator across all refunds.
     */
    @Transactional(readOnly = true)
    public Page<RefundResponseDto> searchAdminRefunds(
            RefundStatus status,
            Long merchantId,
            String search,
            Pageable pageable
    ) {
        return refundRepository.searchAdminRefunds(status, merchantId, search, pageable)
                .map(RefundResponseDto::fromEntity);
    }

    /**
     * Verifies and syncs the status of an UNKNOWN refund by querying the provider.
     */
    public RefundResponseDto verifyRefundStatus(String refundReference, Long merchantId, boolean isAdmin) {
        Refund refund = refundRepository.findByRefundReferenceWithDetails(refundReference)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found with reference: " + refundReference));

        if (!isAdmin && merchantId != null && !refund.getMerchant().getId().equals(merchantId)) {
            throw new PaymentValidationException("Refund does not belong to the authenticated merchant");
        }

        if (refund.getStatus() != RefundStatus.UNKNOWN) {
            log.info("Refund [{}] is not in UNKNOWN status (current: [{}]). Skipping status query.",
                    refundReference, refund.getStatus());
            return RefundResponseDto.fromEntity(refund);
        }

        // Query provider outside DB transaction
        PaymentProviderRefundResponse providerResponse = paymentProvider.queryRefundStatus(
                refund.getProviderRefundId(),
                refundReference
        );

        // Commit outcome in Phase 2 transaction
        return refundTransactionHelper.verifyAndSyncRefundStatus(
                refund.getId(),
                refund.getPayment().getId(),
                providerResponse
        );
    }
}
