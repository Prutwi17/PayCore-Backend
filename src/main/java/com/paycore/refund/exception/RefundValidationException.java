package com.paycore.refund.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class RefundValidationException extends BaseException {
    public RefundValidationException(String message) {
        super("REFUND_VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }
}
