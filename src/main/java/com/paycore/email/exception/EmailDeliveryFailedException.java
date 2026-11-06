package com.paycore.email.exception;

public class EmailDeliveryFailedException extends RuntimeException {

    public EmailDeliveryFailedException(String message) {
        super(message);
    }

    public EmailDeliveryFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
