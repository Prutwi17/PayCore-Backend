package com.paycore.payment.idempotency.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an Idempotency-Key is missing or invalid.
 */
public class IdempotencyValidationException extends BaseException {

    public IdempotencyValidationException(String message) {
        super("IDEMPOTENCY_KEY_INVALID", message, HttpStatus.BAD_REQUEST);
    }
}
