package com.paycore.reconciliation.dto;

import com.paycore.reconciliation.entity.ReconciliationRun;
import com.paycore.reconciliation.entity.RunStatus;
import com.paycore.reconciliation.entity.RunType;

import java.time.Instant;

public class ReconciliationRunResponseDto {

    private Long id;
    private String runReference;
    private RunType runType;
    private RunStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private int totalRecords;
    private int matchedRecords;
    private int discrepancyRecords;
    private int failedRecords;
    private String initiatedBy;
    private String errorSummary;
    private Instant scopeStartDate;
    private Instant scopeEndDate;
    private Instant createdAt;

    public ReconciliationRunResponseDto() {
    }

    public static ReconciliationRunResponseDto fromEntity(ReconciliationRun entity) {
        ReconciliationRunResponseDto dto = new ReconciliationRunResponseDto();
        dto.setId(entity.getId());
        dto.setRunReference(entity.getRunReference());
        dto.setRunType(entity.getRunType());
        dto.setStatus(entity.getStatus());
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setTotalRecords(entity.getTotalRecords());
        dto.setMatchedRecords(entity.getMatchedRecords());
        dto.setDiscrepancyRecords(entity.getDiscrepancyRecords());
        dto.setFailedRecords(entity.getFailedRecords());
        dto.setInitiatedBy(entity.getInitiatedBy());
        dto.setErrorSummary(entity.getErrorSummary());
        dto.setScopeStartDate(entity.getScopeStartDate());
        dto.setScopeEndDate(entity.getScopeEndDate());
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

    public RunType getRunType() {
        return runType;
    }

    public void setRunType(RunType runType) {
        this.runType = runType;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    public int getMatchedRecords() {
        return matchedRecords;
    }

    public void setMatchedRecords(int matchedRecords) {
        this.matchedRecords = matchedRecords;
    }

    public int getDiscrepancyRecords() {
        return discrepancyRecords;
    }

    public void setDiscrepancyRecords(int discrepancyRecords) {
        this.discrepancyRecords = discrepancyRecords;
    }

    public int getFailedRecords() {
        return failedRecords;
    }

    public void setFailedRecords(int failedRecords) {
        this.failedRecords = failedRecords;
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(String initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    public Instant getScopeStartDate() {
        return scopeStartDate;
    }

    public void setScopeStartDate(Instant scopeStartDate) {
        this.scopeStartDate = scopeStartDate;
    }

    public Instant getScopeEndDate() {
        return scopeEndDate;
    }

    public void setScopeEndDate(Instant scopeEndDate) {
        this.scopeEndDate = scopeEndDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
