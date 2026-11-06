package com.paycore.auth.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class PasswordMismatchException extends BaseException {

    public PasswordMismatchException(String message) {
        super("PASSWORD_MISMATCH", message, HttpStatus.BAD_REQUEST);
    }
}
