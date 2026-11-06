package com.paycore.reconciliation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DiscrepancyResolutionRequest {

    @NotBlank(message = "Resolution notes are required")
    @Size(max = 1000, message = "Resolution notes must not exceed 1000 characters")
    private String notes;

    @NotBlank(message = "Resolution reason is required")
    @Size(max = 255, message = "Resolution reason must not exceed 255 characters")
    private String reason;

    public DiscrepancyResolutionRequest() {
    }

    public DiscrepancyResolutionRequest(String notes, String reason) {
        this.notes = notes;
        this.reason = reason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
