package com.paycore.refund.dto;

public class RefundIdempotencyResult {

    private final RefundResponseDto response;
    private final boolean replay;
    private final int httpStatus;

    public RefundIdempotencyResult(RefundResponseDto response, boolean replay, int httpStatus) {
        this.response = response;
        this.replay = replay;
        this.httpStatus = httpStatus;
    }

    public RefundResponseDto getResponse() {
        return response;
    }

    public boolean isReplay() {
        return replay;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
