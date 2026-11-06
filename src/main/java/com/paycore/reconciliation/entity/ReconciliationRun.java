package com.paycore.reconciliation.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "reconciliation_runs",
        indexes = {
                @Index(name = "idx_rec_run_status_created", columnList = "status, created_at DESC"),
                @Index(name = "idx_rec_run_reference", columnList = "run_reference")
        }
)
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_reference", nullable = false, unique = true, length = 64)
    private String runReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 32)
    private RunType runType = RunType.FULL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RunStatus status = RunStatus.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "total_records", nullable = false)
    private int totalRecords = 0;

    @Column(name = "matched_records", nullable = false)
    private int matchedRecords = 0;

    @Column(name = "discrepancy_records", nullable = false)
    private int discrepancyRecords = 0;

    @Column(name = "failed_records", nullable = false)
    private int failedRecords = 0;

    @Column(name = "initiated_by", length = 128)
    private String initiatedBy;

    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    @Column(name = "scope_start_date")
    private Instant scopeStartDate;

    @Column(name = "scope_end_date")
    private Instant scopeEndDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(mappedBy = "reconciliationRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReconciliationRecord> records = new ArrayList<>();

    public ReconciliationRun() {
    }

    public ReconciliationRun(String runReference, RunType runType, String initiatedBy, Instant scopeStartDate, Instant scopeEndDate) {
        this.runReference = runReference;
        this.runType = runType;
        this.initiatedBy = initiatedBy;
        this.scopeStartDate = scopeStartDate;
        this.scopeEndDate = scopeEndDate;
        this.status = RunStatus.PENDING;
        this.createdAt = Instant.now();
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<ReconciliationRecord> getRecords() {
        return records;
    }

    public void setRecords(List<ReconciliationRecord> records) {
        this.records = records;
    }

    public void addRecord(ReconciliationRecord record) {
        records.add(record);
        record.setReconciliationRun(this);
    }
}
