package com.paycore.payment.idempotency.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an existing Idempotency-Key is reused with a different request payload.
 */
public class IdempotencyConflictException extends BaseException {

    public IdempotencyConflictException(String message) {
        super("IDEMPOTENCY_CONFLICT", message, HttpStatus.CONFLICT);
    }
}
