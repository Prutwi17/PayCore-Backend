package com.paycore.auth.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class UnverifiedEmailException extends BaseException {

    public UnverifiedEmailException(String message) {
        super("EMAIL_NOT_VERIFIED", message, HttpStatus.FORBIDDEN);
    }
}
