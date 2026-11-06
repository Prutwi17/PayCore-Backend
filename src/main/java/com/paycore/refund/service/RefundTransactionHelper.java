package com.paycore.refund.service;

import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.event.model.EventType;
import com.paycore.event.model.PayCoreEventBuilder;
import com.paycore.event.outbox.OutboxService;
import com.paycore.payment.entity.Payment;
import com.paycore.payment.entity.PaymentStatus;
import com.paycore.payment.exception.PaymentValidationException;
import com.paycore.payment.provider.PaymentProviderRefundResponse;
import com.paycore.payment.repository.PaymentRepository;
import com.paycore.refund.dto.RefundCreateRequest;
import com.paycore.refund.dto.RefundReservationResult;
import com.paycore.refund.dto.RefundResponseDto;
import com.paycore.refund.entity.Refund;
import com.paycore.refund.entity.RefundStatus;
import com.paycore.refund.exception.ExceededRefundableAmountException;
import com.paycore.refund.exception.PaymentNotRefundableException;
import com.paycore.refund.exception.RefundValidationException;
import com.paycore.refund.repository.RefundRepository;
import com.paycore.refund.statemachine.RefundStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Isolated transaction boundary manager for refund lifecycle operations.
 * Enforces the Zero-DB Network I/O Principle and prevents over-refunding under concurrency
 * using pessimistic write locks on the parent Payment entity.
 * 
 * Atomically records transactional outbox events for all refund state transitions.
 */
@Component
public class RefundTransactionHelper {

    private static final Logger log = LoggerFactory.getLogger(RefundTransactionHelper.class);

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundStateMachine refundStateMachine;
    private final OutboxService outboxService;

    public RefundTransactionHelper(
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            RefundStateMachine refundStateMachine,
            OutboxService outboxService
    ) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.refundStateMachine = refundStateMachine;
        this.outboxService = outboxService;
    }

    /**
     * Phase 1 Transaction:
     * Acquires pessimistic lock on Payment, verifies eligibility, validates balance,
     * atomically reserves requested refund amount, and creates Refund in PROCESSING state.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundReservationResult reserveAndCreateRefund(
            String paymentReference,
            Long merchantId,
            RefundCreateRequest request
    ) {
        // 1. Lock payment row
        Payment payment = paymentRepository.findByPaymentReferenceForUpdate(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with reference: " + paymentReference));

        // 2. Verify merchant ownership
        if (merchantId != null && !payment.getMerchant().getId().equals(merchantId)) {
            log.warn("Merchant [{}] attempted to refund unowned payment [{}]", merchantId, paymentReference);
            throw new PaymentValidationException("Payment does not belong to authenticated merchant");
        }

        // 3. Verify payment refund eligibility
        if (payment.getStatus() != PaymentStatus.SUCCESS && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            log.warn("Payment [{}] in status [{}] cannot be refunded", paymentReference, payment.getStatus());
            throw new PaymentNotRefundableException(
                    String.format("Payment [%s] in status [%s] is not eligible for refund. Only successful payments can be refunded.",
                            paymentReference, payment.getStatus())
            );
        }

        // 4. Verify currency
        if (!payment.getCurrency().equalsIgnoreCase(request.getCurrency().trim())) {
            throw new RefundValidationException(
                    String.format("Refund currency [%s] does not match original payment currency [%s]",
                            request.getCurrency(), payment.getCurrency())
            );
        }

        // 5. Verify refund amount > 0
        BigDecimal refundAmount = request.getAmount();
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RefundValidationException("Refund amount must be greater than zero");
        }

        // 6. Concurrency-safe balance verification: remaining = amount - refunded - reserved
        BigDecimal remainingRefundable = payment.getRemainingRefundableAmount();
        if (refundAmount.compareTo(remainingRefundable) > 0) {
            log.warn("Requested refund amount [{}] exceeds remaining refundable amount [{}] for payment [{}]",
                    refundAmount, remainingRefundable, paymentReference);
            throw new ExceededRefundableAmountException(
                    String.format("Requested refund amount [%s %s] exceeds remaining refundable amount [%s %s]",
                            refundAmount, payment.getCurrency(), remainingRefundable, payment.getCurrency())
            );
        }

        // 7. Atomically reserve refundable amount on payment
        payment.setAmountRefundReserved(payment.getAmountRefundReserved().add(refundAmount));
        paymentRepository.saveAndFlush(payment);

        // 8. Generate unique refund reference and persist Refund record
        String refundReference = "ref_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Refund refund = new Refund();
        refund.setRefundReference(refundReference);
        refund.setPayment(payment);
        refund.setMerchant(payment.getMerchant());
        refund.setAmount(refundAmount);
        refund.setCurrency(payment.getCurrency());
        refund.setReason(request.getReason() != null ? request.getReason().trim() : null);
        refund.setStatus(RefundStatus.REQUESTED);
        refund.setRequestedAt(Instant.now());

        Refund initialSaved = refundRepository.saveAndFlush(refund);

        // Record outbox event: REFUND_CREATED
        outboxService.recordEvent(PayCoreEventBuilder.fromRefund(initialSaved, EventType.REFUND_CREATED));

        // Transition REQUESTED -> PROCESSING
        refundStateMachine.transition(initialSaved, RefundStatus.PROCESSING);
        Refund savedRefund = refundRepository.saveAndFlush(initialSaved);

        // Record outbox event: REFUND_PROCESSING
        outboxService.recordEvent(PayCoreEventBuilder.fromRefund(savedRefund, EventType.REFUND_PROCESSING));

        log.info("Reserved amount [{}] for refund [{}] on payment [{}]. Phase 1 commit.",
                refundAmount, refundReference, paymentReference);

        return new RefundReservationResult(
                savedRefund.getId(),
                refundReference,
                payment.getId(),
                payment.getPaymentReference(),
                payment.getProviderTransactionId(),
                refundAmount,
                payment.getCurrency(),
                request.getReason()
        );
    }

    /**
     * Phase 2 Transaction:
     * Commits final outcome of refund attempt following external provider response.
     * Updates payment balance and refund state machine deterministically.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundResponseDto finalizeRefund(
            Long refundId,
            Long paymentId,
            PaymentProviderRefundResponse providerResponse,
            Exception providerError
    ) {
        // Lock payment row to update balances
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));

        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found for id: " + refundId));

        BigDecimal amount = refund.getAmount();

        EventType outcomeEvent;

        if (providerResponse != null && providerResponse.isSuccess()) {
            // Deduct reserved and add to permanently refunded
            payment.setAmountRefundReserved(payment.getAmountRefundReserved().subtract(amount));
            payment.setAmountRefunded(payment.getAmountRefunded().add(amount));

            // Update payment status
            if (payment.getAmountRefunded().compareTo(payment.getAmount()) >= 0) {
                payment.setStatus(PaymentStatus.REFUNDED);
            } else {
                payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
            }

            refundStateMachine.transition(refund, RefundStatus.SUCCESS);
            refund.setProviderRefundId(providerResponse.getProviderRefundId());
            refund.setFailureCode(null);
            refund.setFailureMessage(null);
            refund.setProcessedAt(Instant.now());
            outcomeEvent = EventType.REFUND_SUCCESS;

            log.info("Refund [{}] finalized SUCCESS for payment [{}]", refund.getRefundReference(), payment.getPaymentReference());
        } else if (providerResponse != null && providerResponse.isFailed()) {
            // Release reserved amount back to available balance
            payment.setAmountRefundReserved(payment.getAmountRefundReserved().subtract(amount));

            refundStateMachine.transition(refund, RefundStatus.FAILED);
            refund.setProviderRefundId(providerResponse.getProviderRefundId());
            refund.setFailureCode(providerResponse.getFailureCode());
            refund.setFailureMessage(providerResponse.getFailureMessage());
            refund.setProcessedAt(Instant.now());
            outcomeEvent = EventType.REFUND_FAILED;

            log.warn("Refund [{}] finalized FAILED for payment [{}]: {}",
                    refund.getRefundReference(), payment.getPaymentReference(), providerResponse.getFailureMessage());
        } else {
            // Downstream timeout or unknown outcome: maintain reservation and set UNKNOWN
            refundStateMachine.transition(refund, RefundStatus.UNKNOWN);
            refund.setFailureCode("PROVIDER_TIMEOUT");
            refund.setFailureMessage(providerError != null ? providerError.getMessage() : "Provider outcome unconfirmed");
            refund.setProcessedAt(Instant.now());
            outcomeEvent = EventType.REFUND_UNKNOWN;

            log.warn("Refund [{}] outcome UNKNOWN for payment [{}]. Retaining reserved balance pending reconciliation.",
                    refund.getRefundReference(), payment.getPaymentReference());
        }

        paymentRepository.saveAndFlush(payment);
        Refund savedRefund = refundRepository.saveAndFlush(refund);

        // Record outbox outcome event
        outboxService.recordEvent(PayCoreEventBuilder.fromRefund(savedRefund, outcomeEvent));

        return RefundResponseDto.fromEntity(savedRefund);
    }

    /**
     * Resolves an UNKNOWN refund following an authoritative provider status query.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundResponseDto verifyAndSyncRefundStatus(
            Long refundId,
            Long paymentId,
            PaymentProviderRefundResponse providerResponse
    ) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));

        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found for id: " + refundId));

        if (refund.getStatus() != RefundStatus.UNKNOWN) {
            log.info("Refund [{}] is already in state [{}]. No verification needed.",
                    refund.getRefundReference(), refund.getStatus());
            return RefundResponseDto.fromEntity(refund);
        }

        BigDecimal amount = refund.getAmount();
        // Transition UNKNOWN -> PROCESSING
        refundStateMachine.transition(refund, RefundStatus.PROCESSING);

        EventType outcomeEvent = null;

        if (providerResponse.isSuccess()) {
            payment.setAmountRefundReserved(payment.getAmountRefundReserved().subtract(amount));
            payment.setAmountRefunded(payment.getAmountRefunded().add(amount));

            if (payment.getAmountRefunded().compareTo(payment.getAmount()) >= 0) {
                payment.setStatus(PaymentStatus.REFUNDED);
            } else {
                payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
            }

            refundStateMachine.transition(refund, RefundStatus.SUCCESS);
            refund.setProviderRefundId(providerResponse.getProviderRefundId());
            refund.setFailureCode(null);
            refund.setFailureMessage(null);
            refund.setProcessedAt(Instant.now());
            outcomeEvent = EventType.REFUND_SUCCESS;
            log.info("Verified refund [{}] as SUCCESS", refund.getRefundReference());
        } else if (providerResponse.isFailed()) {
            payment.setAmountRefundReserved(payment.getAmountRefundReserved().subtract(amount));

            refundStateMachine.transition(refund, RefundStatus.FAILED);
            refund.setFailureCode(providerResponse.getFailureCode());
            refund.setFailureMessage(providerResponse.getFailureMessage());
            refund.setProcessedAt(Instant.now());
            outcomeEvent = EventType.REFUND_FAILED;
            log.warn("Verified refund [{}] as FAILED", refund.getRefundReference());
        } else {
            refundStateMachine.transition(refund, RefundStatus.UNKNOWN);
            log.warn("Status query for refund [{}] returned still UNKNOWN", refund.getRefundReference());
        }

        paymentRepository.saveAndFlush(payment);
        Refund savedRefund = refundRepository.saveAndFlush(refund);

        if (outcomeEvent != null) {
            outboxService.recordEvent(PayCoreEventBuilder.fromRefund(savedRefund, outcomeEvent));
        }

        return RefundResponseDto.fromEntity(savedRefund);
    }
}
