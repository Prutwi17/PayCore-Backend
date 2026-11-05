package com.paycore.refund.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InvalidRefundStateTransitionException extends BaseException {
    public InvalidRefundStateTransitionException(String message) {
        super("INVALID_REFUND_STATE_TRANSITION", message, HttpStatus.CONFLICT);
    }
}
