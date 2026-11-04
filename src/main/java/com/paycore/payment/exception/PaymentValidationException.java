package com.paycore.payment.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class PaymentValidationException extends BaseException {

    public PaymentValidationException(String message) {
        super("PAYMENT_VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }
}
