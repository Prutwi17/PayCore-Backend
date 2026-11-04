package com.paycore.payment.idempotency.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an identical request with the same Idempotency-Key is currently in-flight.
 */
public class IdempotencyProcessingException extends BaseException {

    public IdempotencyProcessingException(String message) {
        super("IDEMPOTENCY_PROCESSING", message, HttpStatus.ACCEPTED);
    }
}
