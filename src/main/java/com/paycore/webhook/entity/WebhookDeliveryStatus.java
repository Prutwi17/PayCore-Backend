package com.paycore.webhook.entity;

public enum WebhookDeliveryStatus {
    SUCCESS,
    PENDING,
    RETRYING,
    FAILED,
    DEAD_LETTER
}
