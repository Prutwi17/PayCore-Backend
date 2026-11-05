package com.paycore.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Unified transaction ledger record (Payment or Refund)")
public class TransactionItemDto {

    private String transactionReference;
    private TransactionType transactionType;
    private String paymentReference;
    private String refundReference;
    private Long merchantId;
    private String merchantCode;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    public TransactionItemDto() {
    }

    public TransactionItemDto(
            String transactionReference,
            TransactionType transactionType,
            String paymentReference,
            String refundReference,
            Long merchantId,
            String merchantCode,
            BigDecimal amount,
            String currency,
            String status,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.transactionReference = transactionReference;
        this.transactionType = transactionType;
        this.paymentReference = paymentReference;
        this.refundReference = refundReference;
        this.merchantId = merchantId;
        this.merchantCode = merchantCode;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getRefundReference() {
        return refundReference;
    }

    public void setRefundReference(String refundReference) {
        this.refundReference = refundReference;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
