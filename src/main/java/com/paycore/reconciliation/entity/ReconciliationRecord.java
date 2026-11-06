package com.paycore.reconciliation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "reconciliation_records",
        indexes = {
                @Index(name = "idx_rec_rec_run_status", columnList = "reconciliation_run_id, status"),
                @Index(name = "idx_rec_rec_internal_ref", columnList = "internal_reference"),
                @Index(name = "idx_rec_rec_provider_ref", columnList = "provider_reference"),
                @Index(name = "idx_rec_rec_discrepancy", columnList = "discrepancy_type"),
                @Index(name = "idx_rec_rec_status", columnList = "status")
        }
)
public class ReconciliationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_run_id", nullable = false)
    private ReconciliationRun reconciliationRun;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 32)
    private RecordType recordType = RecordType.PAYMENT;

    @Column(name = "internal_reference", length = 64)
    private String internalReference;

    @Column(name = "provider_reference", length = 64)
    private String providerReference;

    @Column(name = "internal_amount", precision = 19, scale = 4)
    private BigDecimal internalAmount;

    @Column(name = "provider_amount", precision = 19, scale = 4)
    private BigDecimal providerAmount;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "internal_status", length = 32)
    private String internalStatus;

    @Column(name = "provider_status", length = 32)
    private String providerStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, length = 64)
    private DiscrepancyType discrepancyType = DiscrepancyType.MATCHED;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RecordStatus status = RecordStatus.MATCHED;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "resolved_by", length = 128)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ReconciliationRecord() {
    }

    public ReconciliationRecord(ReconciliationRun reconciliationRun, RecordType recordType,
                                String internalReference, String providerReference,
                                BigDecimal internalAmount, BigDecimal providerAmount,
                                String currency, String internalStatus, String providerStatus,
                                DiscrepancyType discrepancyType, RecordStatus status) {
        this.reconciliationRun = reconciliationRun;
        this.recordType = recordType;
        this.internalReference = internalReference;
        this.providerReference = providerReference;
        this.internalAmount = internalAmount;
        this.providerAmount = providerAmount;
        this.currency = currency;
        this.internalStatus = internalStatus;
        this.providerStatus = providerStatus;
        this.discrepancyType = discrepancyType;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ReconciliationRun getReconciliationRun() {
        return reconciliationRun;
    }

    public void setReconciliationRun(ReconciliationRun reconciliationRun) {
        this.reconciliationRun = reconciliationRun;
    }

    public RecordType getRecordType() {
        return recordType;
    }

    public void setRecordType(RecordType recordType) {
        this.recordType = recordType;
    }

    public String getInternalReference() {
        return internalReference;
    }

    public void setInternalReference(String internalReference) {
        this.internalReference = internalReference;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public BigDecimal getInternalAmount() {
        return internalAmount;
    }

    public void setInternalAmount(BigDecimal internalAmount) {
        this.internalAmount = internalAmount;
    }

    public BigDecimal getProviderAmount() {
        return providerAmount;
    }

    public void setProviderAmount(BigDecimal providerAmount) {
        this.providerAmount = providerAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getInternalStatus() {
        return internalStatus;
    }

    public void setInternalStatus(String internalStatus) {
        this.internalStatus = internalStatus;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public void setProviderStatus(String providerStatus) {
        this.providerStatus = providerStatus;
    }

    public DiscrepancyType getDiscrepancyType() {
        return discrepancyType;
    }

    public void setDiscrepancyType(DiscrepancyType discrepancyType) {
        this.discrepancyType = discrepancyType;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public void setStatus(RecordStatus status) {
        this.status = status;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
