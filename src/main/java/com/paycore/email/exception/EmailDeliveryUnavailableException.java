package com.paycore.email.exception;

public class EmailDeliveryUnavailableException extends RuntimeException {

    public EmailDeliveryUnavailableException(String message) {
        super(message);
    }

    public EmailDeliveryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
