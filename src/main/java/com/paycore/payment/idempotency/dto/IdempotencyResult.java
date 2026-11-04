package com.paycore.payment.idempotency.dto;

import com.paycore.payment.dto.PaymentResponseDto;

/**
 * Result envelope wrapping the payment response and idempotency replay metadata.
 */
public class IdempotencyResult {

    private final PaymentResponseDto response;
    private final boolean replay;
    private final int httpStatus;

    public IdempotencyResult(PaymentResponseDto response, boolean replay, int httpStatus) {
        this.response = response;
        this.replay = replay;
        this.httpStatus = httpStatus;
    }

    public PaymentResponseDto getResponse() {
        return response;
    }

    public boolean isReplay() {
        return replay;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
