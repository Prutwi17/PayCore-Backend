package com.paycore.refund.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ExceededRefundableAmountException extends BaseException {
    public ExceededRefundableAmountException(String message) {
        super("EXCEEDED_REFUNDABLE_AMOUNT", message, HttpStatus.BAD_REQUEST);
    }
}
