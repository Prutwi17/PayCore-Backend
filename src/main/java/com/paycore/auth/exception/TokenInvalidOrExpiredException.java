package com.paycore.auth.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class TokenInvalidOrExpiredException extends BaseException {

    public TokenInvalidOrExpiredException(String message) {
        super("INVALID_OR_EXPIRED_TOKEN", message, HttpStatus.BAD_REQUEST);
    }
}
