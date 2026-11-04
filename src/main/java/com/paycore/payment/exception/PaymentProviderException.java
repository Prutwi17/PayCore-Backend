package com.paycore.payment.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class PaymentProviderException extends BaseException {

    public PaymentProviderException(String message) {
        super("PROVIDER_ERROR", message, HttpStatus.BAD_GATEWAY);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super("PROVIDER_ERROR", message, HttpStatus.BAD_GATEWAY, cause);
    }
}
