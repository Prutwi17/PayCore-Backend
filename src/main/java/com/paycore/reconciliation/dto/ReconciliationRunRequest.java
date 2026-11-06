package com.paycore.reconciliation.dto;

import com.paycore.reconciliation.entity.RunType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class ReconciliationRunRequest {

    @NotNull(message = "Run type is required (PAYMENTS, REFUNDS, or FULL)")
    private RunType runType = RunType.FULL;

    private Instant startDate;
    private Instant endDate;

    public ReconciliationRunRequest() {
    }

    public ReconciliationRunRequest(RunType runType, Instant startDate, Instant endDate) {
        this.runType = runType;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public RunType getRunType() {
        return runType;
    }

    public void setRunType(RunType runType) {
        this.runType = runType;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }
}
