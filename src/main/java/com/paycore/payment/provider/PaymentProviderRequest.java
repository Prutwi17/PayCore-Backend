package com.paycore.payment.provider;

import com.paycore.payment.entity.PaymentMethodType;

import java.math.BigDecimal;

public class PaymentProviderRequest {

    private String paymentReference;
    private String attemptReference;
    private BigDecimal amount;
    private String currency;
    private PaymentMethodType paymentMethod;
    private String customerReference;
    private String description;
    private String simulationScenario;

    public PaymentProviderRequest() {
    }

    public PaymentProviderRequest(String paymentReference, String attemptReference, BigDecimal amount,
                                  String currency, PaymentMethodType paymentMethod, String customerReference,
                                  String description, String simulationScenario) {
        this.paymentReference = paymentReference;
        this.attemptReference = attemptReference;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.customerReference = customerReference;
        this.description = description;
        this.simulationScenario = simulationScenario;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getAttemptReference() {
        return attemptReference;
    }

    public void setAttemptReference(String attemptReference) {
        this.attemptReference = attemptReference;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSimulationScenario() {
        return simulationScenario;
    }

    public void setSimulationScenario(String simulationScenario) {
        this.simulationScenario = simulationScenario;
    }
}
