package com.paycore.reconciliation.dto;

import com.paycore.reconciliation.entity.*;

import java.math.BigDecimal;
import java.time.Instant;

public class ReconciliationRecordResponseDto {

    private Long id;
    private String runReference;
    private RecordType recordType;
    private String internalReference;
    private String providerReference;
    private BigDecimal internalAmount;
    private BigDecimal providerAmount;
    private String currency;
    private String internalStatus;
    private String providerStatus;
    private DiscrepancyType discrepancyType;
    private RecordStatus status;
    private String resolutionNotes;
    private String resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;

    public ReconciliationRecordResponseDto() {
    }

    public static ReconciliationRecordResponseDto fromEntity(ReconciliationRecord entity) {
        ReconciliationRecordResponseDto dto = new ReconciliationRecordResponseDto();
        dto.setId(entity.getId());
        if (entity.getReconciliationRun() != null) {
            dto.setRunReference(entity.getReconciliationRun().getRunReference());
        }
        dto.setRecordType(entity.getRecordType());
        dto.setInternalReference(entity.getInternalReference());
        dto.setProviderReference(entity.getProviderReference());
        dto.setInternalAmount(entity.getInternalAmount());
        dto.setProviderAmount(entity.getProviderAmount());
        dto.setCurrency(entity.getCurrency());
        dto.setInternalStatus(entity.getInternalStatus());
        dto.setProviderStatus(entity.getProviderStatus());
        dto.setDiscrepancyType(entity.getDiscrepancyType());
        dto.setStatus(entity.getStatus());
        dto.setResolutionNotes(entity.getResolutionNotes());
        dto.setResolvedBy(entity.getResolvedBy());
        dto.setResolvedAt(entity.getResolvedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRunReference() {
        return runReference;
    }

    public void setRunReference(String runReference) {
        this.runReference = runReference;
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
