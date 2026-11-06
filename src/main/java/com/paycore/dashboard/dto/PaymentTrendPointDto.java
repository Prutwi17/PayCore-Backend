package com.paycore.dashboard.dto;

import java.math.BigDecimal;

public class PaymentTrendPointDto {

    private String date; // YYYY-MM-DD
    private long totalCount;
    private long successfulCount;
    private long failedCount;
    private BigDecimal totalAmount;

    public PaymentTrendPointDto() {
    }

    public PaymentTrendPointDto(String date, long totalCount, long successfulCount, long failedCount, BigDecimal totalAmount) {
        this.date = date;
        this.totalCount = totalCount;
        this.successfulCount = successfulCount;
        this.failedCount = failedCount;
        this.totalAmount = totalAmount;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public long getSuccessfulCount() {
        return successfulCount;
    }

    public void setSuccessfulCount(long successfulCount) {
        this.successfulCount = successfulCount;
    }

    public long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(long failedCount) {
        this.failedCount = failedCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
