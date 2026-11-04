package com.paycore.payment.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an optimistic locking conflict or race condition prevents safe execution.
 */
public class ConcurrencyConflictException extends BaseException {

    public ConcurrencyConflictException(String message) {
        super("CONCURRENCY_CONFLICT", message, HttpStatus.CONFLICT);
    }
}
