package com.paycore.payment.idempotency.service;

import com.paycore.merchant.entity.Merchant;
import com.paycore.payment.idempotency.entity.IdempotencyProcessingStatus;
import com.paycore.payment.idempotency.entity.IdempotencyRecord;
import com.paycore.payment.idempotency.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Isolated transaction boundary helper for idempotency state persistence.
 */
@Component
public class IdempotencyTransactionHelper {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyTransactionHelper.class);

    private final IdempotencyRecordRepository repository;

    public IdempotencyTransactionHelper(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * Attempts atomic creation of the pending idempotency record.
     * Enforces the uk_merchant_idempotency_key constraint at the database layer.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord createPendingRecord(
            Merchant merchant,
            String idempotencyKey,
            String requestFingerprint,
            Instant expiresAt
    ) {
        IdempotencyRecord record = new IdempotencyRecord(merchant, idempotencyKey, requestFingerprint, expiresAt);
        return repository.saveAndFlush(record);
    }

    /**
     * Marks the idempotency record as COMPLETED and persists the serialized response payload.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long recordId, String paymentReference, int responseStatus, String responseBody) {
        repository.findById(recordId).ifPresent(record -> {
            record.setProcessingStatus(IdempotencyProcessingStatus.COMPLETED);
            record.setPaymentReference(paymentReference);
            record.setResponseStatus(responseStatus);
            record.setResponseBody(responseBody);
            repository.saveAndFlush(record);
            log.info("Marked idempotency record [{}] COMPLETED for payment reference [{}]",
                    recordId, paymentReference);
        });
    }

    /**
     * Marks the idempotency record as FAILED.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long recordId, int responseStatus, String responseBody) {
        repository.findById(recordId).ifPresent(record -> {
            record.setProcessingStatus(IdempotencyProcessingStatus.FAILED);
            record.setResponseStatus(responseStatus);
            record.setResponseBody(responseBody);
            repository.saveAndFlush(record);
            log.warn("Marked idempotency record [{}] FAILED with status [{}]", recordId, responseStatus);
        });
    }

    /**
     * Reads existing record in a read-only transaction.
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findRecord(Long merchantId, String idempotencyKey) {
        return repository.findByMerchantIdAndIdempotencyKeyWithMerchant(merchantId, idempotencyKey);
    }
}
