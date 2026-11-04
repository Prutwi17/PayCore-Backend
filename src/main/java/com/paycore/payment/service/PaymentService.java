package com.paycore.payment.service;

import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.payment.dto.PaymentAttemptResponseDto;
import com.paycore.payment.dto.PaymentCreateRequest;
import com.paycore.payment.dto.PaymentResponseDto;
import com.paycore.payment.dto.PaymentStatusResponseDto;
import com.paycore.payment.entity.Payment;
import com.paycore.payment.entity.PaymentAttempt;
import com.paycore.payment.entity.PaymentStatus;
import com.paycore.payment.exception.InvalidStateTransitionException;
import com.paycore.payment.provider.*;
import com.paycore.payment.repository.PaymentAttemptRepository;
import com.paycore.payment.repository.PaymentRepository;
import com.paycore.payment.statemachine.PaymentStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentTransactionHelper transactionHelper;
    private final PaymentProviderFactory providerFactory;
    private final PaymentStateMachine stateMachine;
    private final PaymentRetryExecutor retryExecutor;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentTransactionHelper transactionHelper,
            PaymentProviderFactory providerFactory,
            PaymentStateMachine stateMachine,
            PaymentRetryExecutor retryExecutor
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.transactionHelper = transactionHelper;
        this.providerFactory = providerFactory;
        this.stateMachine = stateMachine;
        this.retryExecutor = retryExecutor;
    }

    /**
     * Executes the payment processing lifecycle:
     * 1. Transaction 1: Record initial payment in CREATED, queue attempt #1, transition to PROCESSING.
     * 2. Zero-DB Network Call: Invoke configured payment provider outside the active DB transaction.
     * 3. Transaction 2: Persist provider outcome (SUCCESS, FAILED, REQUIRES_ACTION, or UNKNOWN).
     */
    public PaymentResponseDto processPayment(Long merchantId, PaymentCreateRequest request) {
        String paymentReference = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String requestReference = "att_req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);

        log.info("Initiating payment processing for merchant [{}] reference [{}]", merchantId, paymentReference);

        // Transaction 1: Persist initial state
        PaymentTransactionHelper.InitialPaymentContext context = transactionHelper.recordInitialPayment(
                merchantId, request, paymentReference, requestReference
        );

        PaymentProvider provider = providerFactory.getDefaultProvider();
        PaymentProviderRequest providerRequest = new PaymentProviderRequest(
                context.paymentReference(),
                context.requestReference(),
                context.amount(),
                context.currency(),
                context.paymentMethod(),
                context.customerReference(),
                context.description(),
                request.getSimulationScenario()
        );

        PaymentProviderResponse providerResponse = null;
        Throwable providerError = null;

        // Downstream Network I/O - EXECUTED OUTSIDE ANY ACTIVE DATABASE TRANSACTION
        try {
            log.info("Dispatching to payment provider [{}] for reference [{}]",
                    provider.getProviderName(), context.paymentReference());
            providerResponse = provider.initiatePayment(providerRequest);
        } catch (Throwable ex) {
            log.warn("Downstream provider communication failure for payment [{}]: {}",
                    context.paymentReference(), ex.getMessage());
            providerError = ex;
        }

        // Transaction 2: Record outcome
        Payment finalizedPayment = transactionHelper.recordProviderOutcome(
                context.paymentId(),
                context.attemptId(),
                providerResponse,
                providerError
        );

        return PaymentResponseDto.fromEntity(finalizedPayment);
    }

    /**
     * Retrieves payment details by reference with strict merchant ownership enforcement.
     */
    public PaymentResponseDto getPaymentByReference(String paymentReference, Long merchantId, boolean isAdmin) {
        Payment payment = findPaymentWithAccessControl(paymentReference, merchantId, isAdmin);
        return PaymentResponseDto.fromEntity(payment);
    }

    /**
     * Retrieves attempt telemetry for a payment.
     */
    public List<PaymentAttemptResponseDto> getPaymentAttempts(String paymentReference, Long merchantId, boolean isAdmin) {
        Payment payment = findPaymentWithAccessControl(paymentReference, merchantId, isAdmin);
        List<PaymentAttempt> attempts = paymentAttemptRepository.findByPaymentIdOrderByAttemptNumberAsc(payment.getId());
        return attempts.stream()
                .map(PaymentAttemptResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves payment status. If status is UNKNOWN, verifies status with the provider network.
     */
    public PaymentStatusResponseDto getPaymentStatus(String paymentReference, Long merchantId, boolean isAdmin, boolean verifyIfUnknown) {
        Payment payment = findPaymentWithAccessControl(paymentReference, merchantId, isAdmin);

        if (payment.getStatus() == PaymentStatus.UNKNOWN && verifyIfUnknown) {
            log.info("Verifying UNKNOWN payment [{}] with downstream provider using safe query retries", paymentReference);
            try {
                PaymentProvider provider = providerFactory.getProvider(payment.getProvider());
                final Payment currentPayment = payment;
                PaymentProviderResponse verification = retryExecutor.executeSafeQuery(
                        "verifyUnknownStatus:" + paymentReference,
                        () -> provider.queryPaymentStatus(
                                currentPayment.getProviderTransactionId(), currentPayment.getPaymentReference()
                        )
                );
                payment = transactionHelper.recordVerificationOutcome(payment.getId(), verification);
            } catch (Exception ex) {
                log.warn("Status verification call failed for payment [{}]: {}", paymentReference, ex.getMessage());
            }
        }

        return PaymentStatusResponseDto.fromEntity(payment);
    }

    /**
     * Cancels an eligible payment (CREATED, REQUIRES_ACTION, AUTHORIZED).
     */
    public PaymentResponseDto cancelPayment(String paymentReference, Long merchantId, boolean isAdmin, String reason) {
        Payment payment = findPaymentWithAccessControl(paymentReference, merchantId, isAdmin);

        if (!stateMachine.isCancellable(payment.getStatus())) {
            throw new InvalidStateTransitionException(
                    String.format("Payment [%s] in status [%s] cannot be cancelled. Terminal or processing state.",
                            paymentReference, payment.getStatus())
            );
        }

        // If provider transaction exists, notify provider of cancellation
        if (payment.getProviderTransactionId() != null) {
            try {
                PaymentProvider provider = providerFactory.getProvider(payment.getProvider());
                provider.cancelPayment(payment.getProviderTransactionId(), payment.getPaymentReference());
            } catch (Exception e) {
                log.warn("Downstream cancellation request encountered error: {}", e.getMessage());
            }
        }

        Payment cancelled = transactionHelper.cancelPayment(payment.getId(), reason);
        return PaymentResponseDto.fromEntity(cancelled);
    }

    /**
     * Lists payments for a merchant with optional status and query filtering.
     */
    public Page<PaymentResponseDto> listMerchantPayments(
            Long merchantId,
            PaymentStatus status,
            String search,
            Pageable pageable
    ) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        return paymentRepository.searchMerchantPayments(merchantId, status, cleanSearch, pageable)
                .map(PaymentResponseDto::fromEntity);
    }

    /**
     * Lists all payments across merchants for administrative governance.
     */
    public Page<PaymentResponseDto> listAdminPayments(
            PaymentStatus status,
            Long merchantId,
            String search,
            Pageable pageable
    ) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        return paymentRepository.searchAdminPayments(status, merchantId, cleanSearch, pageable)
                .map(PaymentResponseDto::fromEntity);
    }

    private Payment findPaymentWithAccessControl(String paymentReference, Long merchantId, boolean isAdmin) {
        if (isAdmin) {
            return paymentRepository.findByPaymentReferenceWithMerchant(paymentReference)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found with reference: " + paymentReference));
        }

        if (merchantId == null) {
            throw new ResourceNotFoundException("Merchant profile not associated with authenticated user");
        }

        return paymentRepository.findByPaymentReferenceAndMerchantIdWithMerchant(paymentReference, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with reference: " + paymentReference));
    }
}
