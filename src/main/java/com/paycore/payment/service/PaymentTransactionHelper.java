package com.paycore.payment.service;

import com.paycore.merchant.entity.Merchant;
import com.paycore.merchant.entity.MerchantStatus;
import com.paycore.merchant.repository.MerchantRepository;
import com.paycore.payment.dto.PaymentCreateRequest;
import com.paycore.payment.entity.*;
import com.paycore.payment.exception.PaymentProviderTimeoutException;
import com.paycore.payment.exception.PaymentValidationException;
import com.paycore.payment.provider.PaymentProviderResponse;
import com.paycore.payment.provider.PaymentProviderStatus;
import com.paycore.payment.repository.PaymentAttemptRepository;
import com.paycore.payment.repository.PaymentRepository;
import com.paycore.payment.statemachine.PaymentStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Set;

/**
 * Transaction boundary helper for payment operations.
 * Explicitly separates:
 * 1. Initial creation and status transition to PROCESSING (Transaction 1).
 * 2. Downstream payment provider network invocation (ZERO ACTIVE DB TRANSACTIONS).
 * 3. Provider outcome persistence (Transaction 2).
 */
@Component
public class PaymentTransactionHelper {

    private static final Logger log = LoggerFactory.getLogger(PaymentTransactionHelper.class);

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of(
            "INR", "USD", "EUR", "GBP", "CAD", "AUD", "SGD", "AED", "JPY"
    );

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final MerchantRepository merchantRepository;
    private final PaymentStateMachine paymentStateMachine;

    public PaymentTransactionHelper(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            MerchantRepository merchantRepository,
            PaymentStateMachine paymentStateMachine
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.merchantRepository = merchantRepository;
        this.paymentStateMachine = paymentStateMachine;
    }

    /**
     * Transaction 1: Persists payment in CREATED, adds Attempt #1, and transitions to PROCESSING.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InitialPaymentContext recordInitialPayment(
            Long merchantId,
            PaymentCreateRequest request,
            String paymentReference,
            String requestReference
    ) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new PaymentValidationException("Merchant not found with ID: " + merchantId));

        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            throw new PaymentValidationException(
                    String.format("Merchant [%s] is not active. Current status: [%s]",
                            merchant.getMerchantCode(), merchant.getStatus())
            );
        }

        validateCurrencyAndAmount(request.getCurrency(), request.getAmount());

        Payment payment = new Payment();
        payment.setPaymentReference(paymentReference);
        payment.setMerchant(merchant);
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency().toUpperCase());
        payment.setDescription(request.getDescription());
        payment.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethodType.CARD);
        payment.setCustomerReference(request.getCustomerReference());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setProvider("SIMULATED");

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setAttemptNumber(1);
        attempt.setProvider("SIMULATED");
        attempt.setStatus(PaymentAttemptStatus.INITIATED);
        attempt.setRequestReference(requestReference);
        attempt.setStartedAt(Instant.now());

        payment.addAttempt(attempt);
        Payment savedPayment = paymentRepository.save(payment);

        // Transition CREATED -> PROCESSING
        paymentStateMachine.validateTransition(savedPayment.getStatus(), PaymentStatus.PROCESSING);
        savedPayment.setStatus(PaymentStatus.PROCESSING);
        savedPayment = paymentRepository.saveAndFlush(savedPayment);

        PaymentAttempt savedAttempt = savedPayment.getAttempts().get(0);

        log.info("Transaction 1 committed: Payment [{}] transition to PROCESSING. Provider dispatch queued.",
                savedPayment.getPaymentReference());

        return new InitialPaymentContext(
                savedPayment.getId(),
                savedPayment.getPaymentReference(),
                savedAttempt.getId(),
                savedAttempt.getRequestReference(),
                savedPayment.getAmount(),
                savedPayment.getCurrency(),
                savedPayment.getPaymentMethod(),
                savedPayment.getCustomerReference(),
                savedPayment.getDescription()
        );
    }

    /**
     * Transaction 2: Persists downstream provider outcome outside the initial creation transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment recordProviderOutcome(
            Long paymentId,
            Long attemptId,
            PaymentProviderResponse response,
            Throwable error
    ) {
        Payment payment = paymentRepository.findByIdWithMerchant(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment not found with ID: " + paymentId));

        PaymentAttempt attempt = paymentAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalStateException("Payment attempt not found with ID: " + attemptId));

        attempt.setCompletedAt(Instant.now());

        if (error != null) {
            handleProviderError(payment, attempt, error);
        } else if (response != null) {
            handleProviderResponse(payment, attempt, response);
        }

        paymentAttemptRepository.save(attempt);
        Payment updated = paymentRepository.saveAndFlush(payment);

        log.info("Transaction 2 committed: Payment [{}] finalized with status [{}]",
                updated.getPaymentReference(), updated.getStatus());

        return updated;
    }

    /**
     * Transaction 3: Verifies and updates an UNKNOWN payment with the provider's authoritative outcome.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment recordVerificationOutcome(Long paymentId, PaymentProviderResponse response) {
        Payment payment = paymentRepository.findByIdWithMerchant(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment not found with ID: " + paymentId));

        if (payment.getStatus() != PaymentStatus.UNKNOWN) {
            log.warn("Payment [{}] status is [{}], not UNKNOWN. Verification skipped.",
                    payment.getPaymentReference(), payment.getStatus());
            return payment;
        }

        if (response.getStatus() == PaymentProviderStatus.SUCCESS) {
            paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatus.SUCCESS);
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setFailureCode(null);
            payment.setFailureMessage(null);
            payment.setProcessedAt(Instant.now());
        } else if (response.getStatus() == PaymentProviderStatus.FAILED) {
            paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatus.FAILED);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureCode(response.getResponseCode());
            payment.setFailureMessage(response.getResponseMessage());
            payment.setProcessedAt(Instant.now());
        }

        return paymentRepository.saveAndFlush(payment);
    }

    /**
     * Transaction 4: Cancels an active or held payment.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment cancelPayment(Long paymentId, String reason) {
        Payment payment = paymentRepository.findByIdWithMerchant(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment not found with ID: " + paymentId));

        paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatus.CANCELLED);
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setFailureCode("CANCELLED_BY_MERCHANT");
        payment.setFailureMessage(reason != null ? reason : "Payment cancelled by merchant request");
        payment.setProcessedAt(Instant.now());

        return paymentRepository.saveAndFlush(payment);
    }

    private void handleProviderError(Payment payment, PaymentAttempt attempt, Throwable error) {
        if (error instanceof PaymentProviderTimeoutException) {
            paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatus.UNKNOWN);
            payment.setStatus(PaymentStatus.UNKNOWN);
            payment.setFailureCode("PROVIDER_TIMEOUT");
            payment.setFailureMessage(error.getMessage());
            payment.setProcessedAt(Instant.now());

            attempt.setStatus(PaymentAttemptStatus.UNKNOWN);
            attempt.setResponseCode("TIMEOUT");
            attempt.setResponseMessage(error.getMessage());
        } else {
            paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatus.FAILED);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureCode("PROVIDER_COMMUNICATION_ERROR");
            payment.setFailureMessage(error.getMessage());
            payment.setProcessedAt(Instant.now());

            attempt.setStatus(PaymentAttemptStatus.FAILED);
            attempt.setResponseCode("COMM_ERROR");
            attempt.setResponseMessage(error.getMessage());
        }
    }

    private void handleProviderResponse(Payment payment, PaymentAttempt attempt, PaymentProviderResponse response) {
        attempt.setProviderTransactionId(response.getProviderTransactionId());
        attempt.setResponseCode(response.getResponseCode());
        attempt.setResponseMessage(response.getResponseMessage());
        payment.setProviderTransactionId(response.getProviderTransactionId());

        switch (response.getStatus()) {
            case SUCCESS:
                paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatus.SUCCESS);
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setProcessedAt(Instant.now());
                attempt.setStatus(PaymentAttemptStatus.SUCCESS);
                break;

            case FAILED:
                paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatus.FAILED);
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureCode(response.getResponseCode());
                payment.setFailureMessage(response.getResponseMessage());
                payment.setProcessedAt(Instant.now());
                attempt.setStatus(PaymentAttemptStatus.FAILED);
                break;

            case REQUIRES_ACTION:
                paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatus.REQUIRES_ACTION);
                payment.setStatus(PaymentStatus.REQUIRES_ACTION);
                attempt.setStatus(PaymentAttemptStatus.REQUIRES_ACTION);
                break;

            case UNKNOWN:
            default:
                paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatus.UNKNOWN);
                payment.setStatus(PaymentStatus.UNKNOWN);
                payment.setFailureCode("PROVIDER_UNKNOWN");
                payment.setFailureMessage(response.getResponseMessage());
                payment.setProcessedAt(Instant.now());
                attempt.setStatus(PaymentAttemptStatus.UNKNOWN);
                break;
        }
    }

    private void validateCurrencyAndAmount(String currencyCode, BigDecimal amount) {
        if (currencyCode == null || currencyCode.trim().length() != 3) {
            throw new PaymentValidationException("Currency code must be a valid 3-letter ISO 4217 code");
        }

        String normalizedCurrency = currencyCode.trim().toUpperCase();
        if (!SUPPORTED_CURRENCIES.contains(normalizedCurrency)) {
            throw new PaymentValidationException("Unsupported currency: " + normalizedCurrency +
                    ". Supported currencies: " + SUPPORTED_CURRENCIES);
        }

        try {
            Currency currency = Currency.getInstance(normalizedCurrency);
            int maxFractionDigits = currency.getDefaultFractionDigits();
            if (amount.scale() > 4) {
                throw new PaymentValidationException(
                        String.format("Amount precision [%s] exceeds maximum allowed decimal places (4)", amount.scale())
                );
            }
        } catch (IllegalArgumentException e) {
            throw new PaymentValidationException("Invalid ISO 4217 currency code: " + normalizedCurrency);
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentValidationException("Payment amount must be strictly greater than zero");
        }
    }

    public record InitialPaymentContext(
            Long paymentId,
            String paymentReference,
            Long attemptId,
            String requestReference,
            BigDecimal amount,
            String currency,
            PaymentMethodType paymentMethod,
            String customerReference,
            String description
    ) {}
}
