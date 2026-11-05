package com.paycore.refund.dto;

import com.paycore.refund.entity.Refund;
import com.paycore.refund.entity.RefundStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Refund details response DTO")
public class RefundResponseDto {

    private Long id;
    private String refundReference;
    private String paymentReference;
    private Long merchantId;
    private String merchantCode;
    private BigDecimal amount;
    private String currency;
    private RefundStatus status;
    private String reason;
    private String providerRefundId;
    private String failureCode;
    private String failureMessage;
    private Instant requestedAt;
    private Instant processedAt;
    private Instant createdAt;
    private boolean idempotentReplay = false;

    public RefundResponseDto() {
    }

    public static RefundResponseDto fromEntity(Refund refund) {
        RefundResponseDto dto = new RefundResponseDto();
        dto.setId(refund.getId());
        dto.setRefundReference(refund.getRefundReference());
        if (refund.getPayment() != null) {
            dto.setPaymentReference(refund.getPayment().getPaymentReference());
        }
        if (refund.getMerchant() != null) {
            dto.setMerchantId(refund.getMerchant().getId());
            dto.setMerchantCode(refund.getMerchant().getMerchantCode());
        }
        dto.setAmount(refund.getAmount());
        dto.setCurrency(refund.getCurrency());
        dto.setStatus(refund.getStatus());
        dto.setReason(refund.getReason());
        dto.setProviderRefundId(refund.getProviderRefundId());
        dto.setFailureCode(refund.getFailureCode());
        dto.setFailureMessage(refund.getFailureMessage());
        dto.setRequestedAt(refund.getRequestedAt());
        dto.setProcessedAt(refund.getProcessedAt());
        dto.setCreatedAt(refund.getCreatedAt());
        return dto;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRefundReference() {
        return refundReference;
    }

    public void setRefundReference(String refundReference) {
        this.refundReference = refundReference;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public void setMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
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

    public RefundStatus getStatus() {
        return status;
    }

    public void setStatus(RefundStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getProviderRefundId() {
        return providerRefundId;
    }

    public void setProviderRefundId(String providerRefundId) {
        this.providerRefundId = providerRefundId;
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

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isIdempotentReplay() {
        return idempotentReplay;
    }

    public void setIdempotentReplay(boolean idempotentReplay) {
        this.idempotentReplay = idempotentReplay;
    }
}
