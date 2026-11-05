package com.paycore.webhook.exception;

import com.paycore.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class WebhookSsrfException extends BaseException {
    public WebhookSsrfException(String message) {
        super("SSRF_VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }
}
