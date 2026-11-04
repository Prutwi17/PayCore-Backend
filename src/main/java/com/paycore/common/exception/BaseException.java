package com.paycore.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Abstract domain exception defining error code and HTTP status mapping.
 */
public abstract class BaseException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public BaseException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public BaseException(String errorCode, String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
