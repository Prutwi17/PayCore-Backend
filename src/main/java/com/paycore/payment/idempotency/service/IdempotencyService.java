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
import com.paycore.refund.dto.RefundCreateRequest;
import com.paycore.refund.dto.RefundIdempotencyResult;
import com.paycore.refund.dto.RefundResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Orchestrates payment and refund creation with strict idempotency and concurrency controls.
 * <p>
 * Guarantees zero duplicate operations through:
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

        Merchant merchant = validateMerchant(merchantId);

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

    /**
     * Executes the refund workflow under idempotency protection.
     */
    public RefundIdempotencyResult processRefundWithIdempotency(
            Long merchantId,
            String paymentReference,
            String rawIdempotencyKey,
            RefundCreateRequest request,
            Supplier<RefundResponseDto> refundExecutor
    ) {
        validateIdempotencyKey(rawIdempotencyKey);
        String idempotencyKey = rawIdempotencyKey.trim();

        Merchant merchant = validateMerchant(merchantId);

        String requestFingerprint = RequestFingerprintUtil.computeRefundFingerprint(paymentReference, request);
        String lockKey = String.format("lock:refund:idemp:%d:%s", merchantId, idempotencyKey);
        String lockToken = UUID.randomUUID().toString();

        log.info("Processing refund request for merchant [{}] payment [{}] with Idempotency-Key [{}]",
                merchant.getMerchantCode(), paymentReference, idempotencyKey);

        boolean lockAcquired = lockService.acquireLock(lockKey, lockToken, REDIS_LOCK_TTL);

        try {
            Optional<IdempotencyRecord> existingOpt = transactionHelper.findRecord(merchantId, idempotencyKey);
            if (existingOpt.isPresent()) {
                return handleExistingRefundRecord(existingOpt.get(), requestFingerprint, merchantId, idempotencyKey);
            }

            IdempotencyRecord pendingRecord;
            try {
                Instant expiresAt = Instant.now().plus(DEFAULT_TTL);
                pendingRecord = transactionHelper.createPendingRecord(merchant, idempotencyKey, requestFingerprint, expiresAt);
                log.info("Created PENDING idempotency record [{}] for refund on merchant [{}]",
                        pendingRecord.getId(), merchant.getMerchantCode());
            } catch (DataIntegrityViolationException dive) {
                log.info("Collision on unique constraint uk_merchant_idempotency_key for refund key [{}]. Resolving concurrent record.",
                        idempotencyKey);
                return resolveConcurrentRefundRequest(merchantId, idempotencyKey, requestFingerprint);
            }

            RefundResponseDto refundResponse;
            try {
                refundResponse = refundExecutor.get();
                refundResponse.setIdempotentReplay(false);

                String serializedBody = serializeRefundResponse(refundResponse);
                transactionHelper.markCompleted(
                        pendingRecord.getId(),
                        refundResponse.getRefundReference(),
                        201,
                        serializedBody
                );

                return new RefundIdempotencyResult(refundResponse, false, 201);
            } catch (Exception ex) {
                log.error("Refund execution failed for idempotency record [{}]: {}", pendingRecord.getId(), ex.getMessage());
                transactionHelper.markFailed(pendingRecord.getId(), 500, ex.getMessage());
                throw ex;
            }
        } finally {
            if (lockAcquired) {
                lockService.releaseLock(lockKey, lockToken);
            }
        }
    }

    private Merchant validateMerchant(Long merchantId) {
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
        return merchant;
    }

    private IdempotencyResult handleExistingRecord(
            IdempotencyRecord existing,
            String incomingFingerprint,
            Long merchantId,
            String idempotencyKey
    ) {
        if (!existing.getRequestFingerprint().equals(incomingFingerprint)) {
            log.warn("Idempotency conflict detected for merchant [{}] and key [{}]. Fingerprints mismatch.",
                    merchantId, idempotencyKey);
            throw new IdempotencyConflictException(
                    String.format("Idempotency-Key '%s' was already used with a different request payload.", idempotencyKey)
            );
        }

        if (existing.isExpired()) {
            log.warn("Idempotency-Key [{}] has expired for merchant [{}]", idempotencyKey, merchantId);
            if (existing.getProcessingStatus() == IdempotencyProcessingStatus.COMPLETED) {
                return buildReplayResult(existing);
            }
            throw new IdempotencyConflictException(
                    String.format("Idempotency-Key '%s' has expired. Please initiate a new payment with a fresh key.", idempotencyKey)
            );
        }

        if (existing.getProcessingStatus() == IdempotencyProcessingStatus.COMPLETED) {
            log.info("Idempotent replay detected for merchant [{}] and key [{}] (Payment Ref: [{}])",
                    merchantId, idempotencyKey, existing.getPaymentReference());
            return buildReplayResult(existing);
        }

        if (existing.getProcessingStatus() == IdempotencyProcessingStatus.PENDING) {
            log.info("Existing request for key [{}] is currently PENDING. Awaiting concurrent completion.", idempotencyKey);
            return pollForConcurrentCompletion(merchantId, idempotencyKey, incomingFingerprint);
        }

        throw new IdempotencyConflictException(
                String.format("Previous payment attempt with Idempotency-Key '%s' resulted in failure. Please retry with a new key.", idempotencyKey)
        );
    }

    private RefundIdempotencyResult handleExistingRefundRecord(
            IdempotencyRecord existing,
            String incomingFingerprint,
            Long merchantId,
            String idempotencyKey
    ) {
        if (!existing.getRequestFingerprint().equals(incomingFingerprint)) {
            log.warn("Idempotency conflict detected for refund merchant [{}] and key [{}]. Fingerprints mismatch.",
                    merchantId, idempotencyKey);
            throw new IdempotencyConflictException(
                    String.format("Idempotency-Key '%s' was already used with a different request payload.", idempotencyKey)
            );
        }

        if (existing.isExpired()) {
            log.warn("Idempotency-Key [{}] has expired for merchant [{}]", idempotencyKey, merchantId);
            if (existing.getProcessingStatus() == IdempotencyProcessingStatus.COMPLETED) {
                return buildRefundReplayResult(existing);
            }
            throw new IdempotencyConflictException(
                    String.format("Idempotency-Key '%s' has expired. Please initiate a new refund with a fresh key.", idempotencyKey)
            );
        }

        if (existing.getProcessingStatus() == IdempotencyProcessingStatus.COMPLETED) {
            log.info("Idempotent replay detected for refund merchant [{}] and key [{}] (Ref: [{}])",
                    merchantId, idempotencyKey, existing.getPaymentReference());
            return buildRefundReplayResult(existing);
        }

        if (existing.getProcessingStatus() == IdempotencyProcessingStatus.PENDING) {
            log.info("Existing refund request for key [{}] is currently PENDING. Awaiting concurrent completion.", idempotencyKey);
            return pollForConcurrentRefundCompletion(merchantId, idempotencyKey, incomingFingerprint);
        }

        throw new IdempotencyConflictException(
                String.format("Previous refund attempt with Idempotency-Key '%s' resulted in failure. Please retry with a new key.", idempotencyKey)
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

    private RefundIdempotencyResult resolveConcurrentRefundRequest(
            Long merchantId,
            String idempotencyKey,
            String requestFingerprint
    ) {
        Optional<IdempotencyRecord> recordOpt = transactionHelper.findRecord(merchantId, idempotencyKey);
        if (recordOpt.isPresent()) {
            return handleExistingRefundRecord(recordOpt.get(), requestFingerprint, merchantId, idempotencyKey);
        }
        return pollForConcurrentRefundCompletion(merchantId, idempotencyKey, requestFingerprint);
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

        log.warn("Concurrent request for key [{}] still in progress after poll window", idempotencyKey);
        throw new IdempotencyProcessingException(
                String.format("Payment operation with Idempotency-Key '%s' is currently being processed. Please query status shortly.", idempotencyKey)
        );
    }

    private RefundIdempotencyResult pollForConcurrentRefundCompletion(
            Long merchantId,
            String idempotencyKey,
            String requestFingerprint
    ) {
        for (int i = 0; i < CONCURRENT_POLL_MAX_ATTEMPTS; i++) {
            try {
                Thread.sleep(CONCURRENT_POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting concurrent refund idempotency completion", ie);
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
                    log.info("Concurrent refund request for key [{}] completed during polling", idempotencyKey);
                    return buildRefundReplayResult(record);
                }
            }
        }

        log.warn("Concurrent refund request for key [{}] still in progress after poll window", idempotencyKey);
        throw new IdempotencyProcessingException(
                String.format("Refund operation with Idempotency-Key '%s' is currently being processed. Please query status shortly.", idempotencyKey)
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

        if (record.getPaymentReference() != null) {
            PaymentResponseDto dto = paymentService.getPaymentByReference(
                    record.getPaymentReference(), record.getMerchant().getId(), false
            );
            dto.setIdempotentReplay(true);
            return new IdempotencyResult(dto, true, 200);
        }

        throw new IllegalStateException("Idempotency record completed but lacks payment reference");
    }

    private RefundIdempotencyResult buildRefundReplayResult(IdempotencyRecord record) {
        if (record.getResponseBody() != null && !record.getResponseBody().isBlank()) {
            try {
                RefundResponseDto dto = objectMapper.readValue(record.getResponseBody(), RefundResponseDto.class);
                dto.setIdempotentReplay(true);
                return new RefundIdempotencyResult(dto, true, 200);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached refund idempotency response body: {}", e.getMessage());
            }
        }

        throw new IllegalStateException("Refund idempotency record completed but lacks serialized response body");
    }

    private String serializeResponse(PaymentResponseDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PaymentResponseDto for idempotency cache: {}", e.getMessage());
            return "{}";
        }
    }

    private String serializeRefundResponse(RefundResponseDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize RefundResponseDto for idempotency cache: {}", e.getMessage());
            return "{}";
        }
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IdempotencyValidationException("Idempotency-Key header is required");
        }
        if (key.trim().length() > 128) {
            throw new IdempotencyValidationException("Idempotency-Key length must not exceed 128 characters");
        }
    }
}
