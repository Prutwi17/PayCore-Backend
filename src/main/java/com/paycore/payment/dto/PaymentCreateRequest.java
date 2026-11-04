package com.paycore.payment.dto;

import com.paycore.payment.entity.PaymentMethodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Payload for initiating a new payment")
public class PaymentCreateRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be strictly greater than 0")
    @Schema(description = "Payment amount formatted with up to 4 decimal places", example = "1499.50")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO 4217 code (e.g. INR, USD, EUR)")
    @Schema(description = "ISO 4217 currency code", example = "INR")
    private String currency;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    @Schema(description = "Optional transaction description or order summary", example = "Order #9021 payment")
    private String description;

    @Schema(description = "Selected payment method category", example = "CARD")
    private PaymentMethodType paymentMethod = PaymentMethodType.CARD;

    @Size(max = 120, message = "Customer reference must not exceed 120 characters")
    @Schema(description = "External merchant customer reference or customer email", example = "cust_user_42@example.com")
    private String customerReference;

    @Schema(description = "Deterministic test simulation scenario (SUCCESS, FAIL, TIMEOUT, REQUIRES_ACTION)", example = "SUCCESS")
    private String simulationScenario;

    public PaymentCreateRequest() {
    }

    public PaymentCreateRequest(BigDecimal amount, String currency, String description,
                                PaymentMethodType paymentMethod, String customerReference) {
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.paymentMethod = paymentMethod;
        this.customerReference = customerReference;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PaymentMethodType getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethodType paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public void setCustomerReference(String customerReference) {
        this.customerReference = customerReference;
    }

    public String getSimulationScenario() {
        return simulationScenario;
    }

    public void setSimulationScenario(String simulationScenario) {
        this.simulationScenario = simulationScenario;
    }
}
