package com.paycore.payment.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends BaseException {

    public InvalidStateTransitionException(String currentStatus, String targetStatus) {
        super("INVALID_STATE_TRANSITION",
                String.format("Invalid payment state transition from [%s] to [%s]", currentStatus, targetStatus),
                HttpStatus.CONFLICT);
    }

    public InvalidStateTransitionException(String message) {
        super("INVALID_STATE_TRANSITION", message, HttpStatus.CONFLICT);
    }
}
