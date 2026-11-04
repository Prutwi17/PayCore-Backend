package com.paycore.payment.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class PaymentProviderTimeoutException extends BaseException {

    public PaymentProviderTimeoutException(String message) {
        super("PROVIDER_TIMEOUT", message, HttpStatus.GATEWAY_TIMEOUT);
    }

    public PaymentProviderTimeoutException(String message, Throwable cause) {
        super("PROVIDER_TIMEOUT", message, HttpStatus.GATEWAY_TIMEOUT, cause);
    }
}
