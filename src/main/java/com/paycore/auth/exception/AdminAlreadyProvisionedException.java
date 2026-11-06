package com.paycore.auth.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class AdminAlreadyProvisionedException extends BaseException {

    public AdminAlreadyProvisionedException(String message) {
        super("ADMIN_ALREADY_PROVISIONED", message, HttpStatus.BAD_REQUEST);
    }
}
