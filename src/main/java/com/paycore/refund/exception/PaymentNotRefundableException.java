package com.paycore.refund.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class PaymentNotRefundableException extends BaseException {
    public PaymentNotRefundableException(String message) {
        super("PAYMENT_NOT_REFUNDABLE", message, HttpStatus.BAD_REQUEST);
    }
}
