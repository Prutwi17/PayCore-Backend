package com.paycore.payment.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.merchant.entity.Merchant;
import com.paycore.merchant.entity.MerchantStatus;
import com.paycore.merchant.repository.MerchantRepository;
import com.paycore.payment.dto.PaymentCreateRequest;
import com.paycore.payment.dto.PaymentResponseDto;
import com.paycore.payment.exception.PaymentValidationException;
import com.paycore.payment.idempotency.dto.IdempotencyResult;
import com.paycore.payment.idempotency.entity.IdempotencyProcessingStatus;
import com.paycore.payment.idempotency.entity.IdempotencyRecord;
import com.paycore.payment.idempotency.exception.IdempotencyConflictException;
import com.paycore.payment.idempotency.exception.IdempotencyProcessingException;
import com.paycore.payment.idempotency.exception.IdempotencyValidationException;
import com.paycore.payment.idempotency.util.RequestFingerprintUtil;
import com.paycore.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates payment creation with strict idempotency and concurrency controls.
 * <p>
 * Guarantees zero duplicate charges through:
 * 1. Redis distributed lock (fast coordination path).
 * 2. MySQL composite unique constraint uk_merchant_idempotency_key on (merchant_id, idempotency_key).
 * 3. Request fingerprinting using canonical SHA-256 digests.
 * 4. Safe concurrent observation loop for in-flight requests.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final Duration REDIS_LOCK_TTL = Duration.ofSeconds(15);
    private static final int CONCURRENT_POLL_MAX_ATTEMPTS = 15;
    private static final long CONCURRENT_POLL_INTERVAL_MS = 150;

    private final MerchantRepository merchantRepository;
    private final IdempotencyTransactionHelper transactionHelper;
    private final DistributedLockService lockService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public IdempotencyService(
            MerchantRepository merchantRepository,
            IdempotencyTransactionHelper transactionHelper,
            DistributedLockService lockService,
            PaymentService paymentService,
            ObjectMapper objectMapper
    ) {
        this.merchantRepository = merchantRepository;
        this.transactionHelper = transactionHelper;
        this.lockService = lockService;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes the payment creation workflow under idempotency protection.
     */
    public IdempotencyResult processPaymentWithIdempotency(
            Long merchantId,
            String rawIdempotencyKey,
            PaymentCreateRequest request
    ) {
        validateIdempotencyKey(rawIdempotencyKey);
        String idempotencyKey = rawIdempotencyKey.trim();

        if (merchantId == null) {
            throw new ResourceNotFoundException("Merchant profile not associated with authenticated user");
        }

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found with ID: " + merchantId));

        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            throw new PaymentValidationException(
                    String.format("Merchant [%s] is not active. Current status: [%s]",
                            merchant.getMerchantCode(), merchant.getStatus())
            );
        }

        String requestFingerprint = RequestFingerprintUtil.computeFingerprint(request);
        String lockKey = String.format("lock:payment:idemp:%d:%s", merchantId, idempotencyKey);
        String lockToken = UUID.randomUUID().toString();

        log.info("Processing payment request for merchant [{}] with Idempotency-Key [{}] and Fingerprint [{}]",
                merchant.getMerchantCode(), idempotencyKey, requestFingerprint);

        // Optional Redis lock acquisition for distributed coordination
        boolean lockAcquired = lockService.acquireLock(lockKey, lockToken, REDIS_LOCK_TTL);

        try {
            // First check if an existing record is present
            Optional<IdempotencyRecord> existingOpt = transactionHelper.findRecord(merchantId, idempotencyKey);
            if (existingOpt.isPresent()) {
                return handleExistingRecord(existingOpt.get(), requestFingerprint, merchantId, idempotencyKey);
            }

            // Attempt atomic insert of PENDING record (enforces DB unique constraint)
            IdempotencyRecord pendingRecord;
            try {
                Instant expiresAt = Instant.now().plus(DEFAULT_TTL);
                pendingRecord = transactionHelper.createPendingRecord(merchant, idempotencyKey, requestFingerprint, expiresAt);
                log.info("Created PENDING idempotency record [{}] for merchant [{}]",
                        pendingRecord.getId(), merchant.getMerchantCode());
            } catch (DataIntegrityViolationException dive) {
                log.info("Collision on unique constraint uk_merchant_idempotency_key for key [{}]. Resolving concurrent record.",
                        idempotencyKey);
                // Race condition: another thread inserted the record concurrently
                return resolveConcurrentRequest(merchantId, idempotencyKey, requestFingerprint);
            }

            // CASE 1: First Request - Execute the payment creation
            PaymentResponseDto paymentResponse;
            try {
                paymentResponse = paymentService.processPayment(merchantId, request);
                paymentResponse.setIdempotentReplay(false);

                String serializedBody = serializeResponse(paymentResponse);
                transactionHelper.markCompleted(
                        pendingRecord.getId(),
                        paymentResponse.getPaymentReference(),
                        201,
                        serializedBody
                );

                return new IdempotencyResult(paymentResponse, false, 201);
            } catch (Exception ex) {
                log.error("Payment execution failed for idempotency record [{}]: {}", pendingRecord.getId(), ex.getMessage());
                transactionHelper.markFailed(pendingRecord.getId(), 500, ex.getMessage());
                throw ex;
            }
        } finally {
            if (lockAcquired) {
                lockService.releaseLock(lockKey, lockToken);
            }
        }
    }

    private IdempotencyResult handleExistingRecord(
            IdempotencyRecord existing,
            String incomingFingerprint,
            Long merchantId,
            String idempotencyKey
    ) {
        // CASE 3: Payload Conflict - Same key with different request payload
        if (!existing.getRequestFingerprint().equals(incomingFingerprint)) {
            log.warn("Idempotency conflict detected for merchant [{}] and key [{}]. Fingerprints mismatch.",
                    merchantId, idempotencyKey);
            throw new IdempotencyConflictException(
                    String.format("Idempotency-Key '%s' was already used with a different request payload.", idempotencyKey)
            );
        }

        // CASE 6: Expiration Check
        if (existing.isExpired()) {
            log.warn("Idempotency-Key [{}] has expired for merchant [{}]", idempotencyKey, merchantId);
            // If underlying payment completed, return the result rather than duplicating
            if (existing.getProcessingStatus() == IdempotencyProcessingStatus.COMPLETED) {
                return buildReplayResult(existing);
            }
            throw new IdempotencyConflictException(
                    String.format("Idempotency-Key '%s' has expired. Please initiate a new payment with a fresh key.", idempotencyKey)
            );
        }

        // CASE 2: Same key and same request (COMPLETED)
        if (existing.getProcessingStatus() == IdempotencyProcessingStatus.COMPLETED) {
            log.info("Idempotent replay detected for merchant [{}] and key [{}] (Payment Ref: [{}])",
                    merchantId, idempotencyKey, existing.getPaymentReference());
            return buildReplayResult(existing);
        }

        // CASE 4 & 5: Request is currently PENDING/PROCESSING
        if (existing.getProcessingStatus() == IdempotencyProcessingStatus.PENDING) {
            log.info("Existing request for key [{}] is currently PENDING. Awaiting concurrent completion.", idempotencyKey);
            return pollForConcurrentCompletion(merchantId, idempotencyKey, incomingFingerprint);
        }

        // Previously FAILED request
        throw new IdempotencyConflictException(
                String.format("Previous payment attempt with Idempotency-Key '%s' resulted in failure. Please retry with a new key.", idempotencyKey)
        );
    }

    private IdempotencyResult resolveConcurrentRequest(
            Long merchantId,
            String idempotencyKey,
            String requestFingerprint
    ) {
        Optional<IdempotencyRecord> recordOpt = transactionHelper.findRecord(merchantId, idempotencyKey);
        if (recordOpt.isPresent()) {
            return handleExistingRecord(recordOpt.get(), requestFingerprint, merchantId, idempotencyKey);
        }
        return pollForConcurrentCompletion(merchantId, idempotencyKey, requestFingerprint);
    }

    private IdempotencyResult pollForConcurrentCompletion(
            Long merchantId,
            String idempotencyKey,
            String requestFingerprint
    ) {
        for (int i = 0; i < CONCURRENT_POLL_MAX_ATTEMPTS; i++) {
            try {
                Thread.sleep(CONCURRENT_POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting concurrent idempotency completion", ie);
            }

            Optional<IdempotencyRecord> current = transactionHelper.findRecord(merchantId, idempotencyKey);
            if (current.isPresent()) {
                IdempotencyRecord record = current.get();

                if (!record.getRequestFingerprint().equals(requestFingerprint)) {
                    throw new IdempotencyConflictException(
                            String.format("Idempotency-Key '%s' was already used with a different request payload.", idempotencyKey)
                    );
                }

                if (record.getProcessingStatus() == IdempotencyProcessingStatus.COMPLETED) {
                    log.info("Concurrent request for key [{}] completed during polling", idempotencyKey);
                    return buildReplayResult(record);
                }
            }
        }

        // Still PENDING after poll window: Return 202 Accepted processing response
        log.warn("Concurrent request for key [{}] still in progress after poll window", idempotencyKey);
        throw new IdempotencyProcessingException(
                String.format("Payment operation with Idempotency-Key '%s' is currently being processed. Please query payment status shortly.", idempotencyKey)
        );
    }

    private IdempotencyResult buildReplayResult(IdempotencyRecord record) {
        if (record.getResponseBody() != null && !record.getResponseBody().isBlank()) {
            try {
                PaymentResponseDto dto = objectMapper.readValue(record.getResponseBody(), PaymentResponseDto.class);
                dto.setIdempotentReplay(true);
                return new IdempotencyResult(dto, true, 200);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached idempotency response body: {}. Fetching fresh from repository.", e.getMessage());
            }
        }

        // Fallback: load directly from payment service
        if (record.getPaymentReference() != null) {
            PaymentResponseDto dto = paymentService.getPaymentByReference(
                    record.getPaymentReference(), record.getMerchant().getId(), false
            );
            dto.setIdempotentReplay(true);
            return new IdempotencyResult(dto, true, 200);
        }

        throw new IllegalStateException("Idempotency record completed but lacks payment reference");
    }

    private String serializeResponse(PaymentResponseDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PaymentResponseDto for idempotency cache: {}", e.getMessage());
            return "{}";
        }
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IdempotencyValidationException("Idempotency-Key header is required for payment initiation");
        }
        if (key.trim().length() > 128) {
            throw new IdempotencyValidationException("Idempotency-Key length must not exceed 128 characters");
        }
    }
}
