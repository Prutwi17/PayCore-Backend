package com.paycore.transaction.entity;

import com.paycore.transaction.dto.TransactionItemDto;
import com.paycore.transaction.dto.TransactionType;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-only database view entity synthesizing a unified audit ledger across payments and refunds.
 * Leverages database-level pagination, sorting, and filtering without in-memory buffering.
 */
@Entity
@Immutable
@Table(name = "v_transactions")
@Subselect(
        "SELECT " +
        "'PAYMENT' AS transaction_type, " +
        "p.payment_reference AS transaction_reference, " +
        "p.payment_reference AS payment_reference, " +
        "CAST(NULL AS CHAR(64)) AS refund_reference, " +
        "p.merchant_id AS merchant_id, " +
        "m.merchant_code AS merchant_code, " +
        "p.amount AS amount, " +
        "p.currency AS currency, " +
        "p.status AS status, " +
        "p.created_at AS created_at, " +
        "p.updated_at AS updated_at, " +
        "p.description AS description " +
        "FROM payments p " +
        "JOIN merchants m ON p.merchant_id = m.id " +
        "UNION ALL " +
        "SELECT " +
        "'REFUND' AS transaction_type, " +
        "r.refund_reference AS transaction_reference, " +
        "p.payment_reference AS payment_reference, " +
        "r.refund_reference AS refund_reference, " +
        "r.merchant_id AS merchant_id, " +
        "m.merchant_code AS merchant_code, " +
        "r.amount AS amount, " +
        "r.currency AS currency, " +
        "r.status AS status, " +
        "r.created_at AS created_at, " +
        "r.updated_at AS updated_at, " +
        "r.reason AS description " +
        "FROM refunds r " +
        "JOIN payments p ON r.payment_id = p.id " +
        "JOIN merchants m ON r.merchant_id = m.id"
)
public class TransactionView {

    @Id
    @Column(name = "transaction_reference")
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type")
    private TransactionType transactionType;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "refund_reference")
    private String refundReference;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "merchant_code")
    private String merchantCode;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "description")
    private String description;

    public TransactionView() {
    }

    public TransactionItemDto toDto() {
        return new TransactionItemDto(
                this.transactionReference,
                this.transactionType,
                this.paymentReference,
                this.refundReference,
                this.merchantId,
                this.merchantCode,
                this.amount,
                this.currency,
                this.status,
                this.description,
                this.createdAt,
                this.updatedAt
        );
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public String getRefundReference() {
        return refundReference;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getDescription() {
        return description;
    }
}
