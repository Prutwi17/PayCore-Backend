package com.paycore.payment.dto;

import com.paycore.payment.entity.Payment;
import com.paycore.payment.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Lightweight payment status response")
public class PaymentStatusResponseDto {

    private String paymentReference;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String failureCode;
    private String failureMessage;
    private Instant processedAt;

    public PaymentStatusResponseDto() {
    }

    public static PaymentStatusResponseDto fromEntity(Payment payment) {
        PaymentStatusResponseDto dto = new PaymentStatusResponseDto();
        dto.setPaymentReference(payment.getPaymentReference());
        dto.setStatus(payment.getStatus());
        dto.setAmount(payment.getAmount());
        dto.setCurrency(payment.getCurrency());
        dto.setFailureCode(payment.getFailureCode());
        dto.setFailureMessage(payment.getFailureMessage());
        dto.setProcessedAt(payment.getProcessedAt());
        return dto;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
