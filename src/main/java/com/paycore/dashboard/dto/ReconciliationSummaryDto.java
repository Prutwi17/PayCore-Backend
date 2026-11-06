package com.paycore.dashboard.dto;

import java.time.Instant;
import java.util.Map;

public class ReconciliationSummaryDto {

    private long totalRuns;
    private long unresolvedDiscrepancies;
    private String latestRunReference;
    private String latestRunStatus;
    private Instant latestRunCompletedAt;
    private Map<String, Long> discrepancyTypeCounts;

    public ReconciliationSummaryDto() {
    }

    public long getTotalRuns() {
        return totalRuns;
    }

    public void setTotalRuns(long totalRuns) {
        this.totalRuns = totalRuns;
    }

    public long getUnresolvedDiscrepancies() {
        return unresolvedDiscrepancies;
    }

    public void setUnresolvedDiscrepancies(long unresolvedDiscrepancies) {
        this.unresolvedDiscrepancies = unresolvedDiscrepancies;
    }

    public String getLatestRunReference() {
        return latestRunReference;
    }

    public void setLatestRunReference(String latestRunReference) {
        this.latestRunReference = latestRunReference;
    }

    public String getLatestRunStatus() {
        return latestRunStatus;
    }

    public void setLatestRunStatus(String latestRunStatus) {
        this.latestRunStatus = latestRunStatus;
    }

    public Instant getLatestRunCompletedAt() {
        return latestRunCompletedAt;
    }

    public void setLatestRunCompletedAt(Instant latestRunCompletedAt) {
        this.latestRunCompletedAt = latestRunCompletedAt;
    }

    public Map<String, Long> getDiscrepancyTypeCounts() {
        return discrepancyTypeCounts;
    }

    public void setDiscrepancyTypeCounts(Map<String, Long> discrepancyTypeCounts) {
        this.discrepancyTypeCounts = discrepancyTypeCounts;
    }
}
