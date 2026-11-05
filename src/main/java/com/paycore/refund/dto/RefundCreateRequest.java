package com.paycore.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Request payload to initiate a full or partial refund")
public class RefundCreateRequest {

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than zero")
    @Schema(description = "Refund amount (supports full or partial payment amount)", example = "25.00")
    private BigDecimal amount;

    @NotBlank(message = "Currency code is required")
    @Size(min = 3, max = 3, message = "Currency must be an ISO 4217 3-letter code")
    @Schema(description = "ISO 4217 3-letter currency code (must match original payment currency)", example = "USD")
    private String currency;

    @Size(max = 255, message = "Reason cannot exceed 255 characters")
    @Schema(description = "Reason or note for issuing the refund", example = "Customer returned damaged item")
    private String reason;

    public RefundCreateRequest() {
    }

    public RefundCreateRequest(BigDecimal amount, String currency, String reason) {
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
