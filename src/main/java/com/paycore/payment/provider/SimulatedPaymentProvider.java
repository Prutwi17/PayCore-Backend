package com.paycore.payment.provider;

import com.paycore.payment.exception.PaymentProviderTimeoutException;
import com.paycore.reconciliation.adapter.SimulatedProviderRecordStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic simulated payment provider for local development, automated testing,
 * and sandbox demonstrations.
 * Automatically populates an independent provider statement store for reconciliation.
 */
@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentProvider.class);
    public static final String PROVIDER_NAME = "SIMULATED";

    private final SimulatedProviderRecordStore recordStore;

    public SimulatedPaymentProvider() {
        this.recordStore = null;
    }

    @Autowired
    public SimulatedPaymentProvider(Optional<SimulatedProviderRecordStore> recordStoreOpt) {
        this.recordStore = recordStoreOpt.orElse(null);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public PaymentProviderResponse initiatePayment(PaymentProviderRequest request) {
        String txId = "sim_tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String scenario = resolvePaymentScenario(request);

        log.info("SimulatedPaymentProvider processing payment reference [{}] with scenario [{}]",
                request.getPaymentReference(), scenario);

        PaymentProviderResponse response;
        switch (scenario.toUpperCase()) {
            case "FAIL":
            case "DECLINE":
                response = PaymentProviderResponse.failed(
                        txId,
                        "DECLINED_INSUFFICIENT_FUNDS",
                        "The simulated card issuer reported insufficient account funds."
                );
                recordTransaction(txId, request, "FAILED", response.getResponseCode(), response.getResponseMessage());
                return response;

            case "REQUIRES_ACTION":
            case "3DS":
                response = PaymentProviderResponse.requiresAction(
                        txId,
                        "https://sandbox.paycore.internal/3ds/verify?tx=" + txId,
                        "Customer 3D-Secure authentication required."
                );
                recordTransaction(txId, request, "PENDING", response.getResponseCode(), response.getResponseMessage());
                return response;

            case "TIMEOUT":
            case "UNKNOWN":
                log.warn("SimulatedPaymentProvider simulating downstream socket read timeout for tx [{}]", txId);
                throw new PaymentProviderTimeoutException("Downstream simulated provider timed out after 10000ms. Status unconfirmed.");

            case "SUCCESS":
            default:
                response = PaymentProviderResponse.success(
                        txId,
                        "200_APPROVED",
                        "Payment authorized and settled successfully by simulated provider."
                );
                recordTransaction(txId, request, "SUCCESS", response.getResponseCode(), response.getResponseMessage());
                return response;
        }
    }

    @Override
    public PaymentProviderResponse queryPaymentStatus(String providerTransactionId, String requestReference) {
        log.info("SimulatedPaymentProvider querying status for provider tx [{}]", providerTransactionId);
        return PaymentProviderResponse.success(
                providerTransactionId,
                "200_CONFIRMED",
                "Transaction status confirmed as SUCCESS by simulated network."
        );
    }

    @Override
    public PaymentProviderResponse cancelPayment(String providerTransactionId, String requestReference) {
        log.info("SimulatedPaymentProvider cancelling transaction [{}]", providerTransactionId);
        if (recordStore != null) {
            recordStore.updatePaymentStatus(providerTransactionId, "CANCELLED");
        }
        return PaymentProviderResponse.cancelled(
                providerTransactionId,
                "Authorization hold successfully voided by simulated provider."
        );
    }

    @Override
    public PaymentProviderRefundResponse initiateRefund(PaymentProviderRefundRequest request) {
        String refundTxId = "sim_ref_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String scenario = resolveRefundScenario(request);

        log.info("SimulatedPaymentProvider processing refund reference [{}] for payment [{}] with scenario [{}]",
                request.getRefundReference(), request.getPaymentReference(), scenario);

        PaymentProviderRefundResponse response;
        switch (scenario.toUpperCase()) {
            case "FAIL":
            case "DECLINE":
                response = PaymentProviderRefundResponse.failed(
                        refundTxId,
                        "REFUND_DECLINED",
                        "Simulated refund was declined by customer issuing bank."
                );
                recordRefund(refundTxId, request, "FAILED", response.getFailureCode(), response.getFailureMessage());
                return response;

            case "TIMEOUT":
            case "UNKNOWN":
                log.warn("SimulatedPaymentProvider simulating network timeout during refund [{}]", refundTxId);
                throw new PaymentProviderTimeoutException("Simulated provider timeout during refund initiation. Outcome unknown.");

            case "SUCCESS":
            default:
                response = PaymentProviderRefundResponse.success(
                        refundTxId,
                        "200_REFUND_APPROVED",
                        "Simulated refund settled successfully."
                );
                recordRefund(refundTxId, request, "SUCCESS", "200_REFUND_APPROVED", "Simulated refund settled successfully.");
                return response;
        }
    }

    @Override
    public PaymentProviderRefundResponse queryRefundStatus(String providerRefundId, String refundReference) {
        log.info("SimulatedPaymentProvider querying refund status for [{}] / [{}]", providerRefundId, refundReference);
        if (providerRefundId != null && providerRefundId.contains("FAIL")) {
            return PaymentProviderRefundResponse.failed(
                    providerRefundId,
                    "REFUND_DECLINED",
                    "Simulated status query confirmed refund failure."
            );
        }
        return PaymentProviderRefundResponse.success(
                providerRefundId != null ? providerRefundId : "sim_ref_verified",
                "200_CONFIRMED",
                "Simulated status query confirmed refund settlement."
        );
    }

    private void recordTransaction(String txId, PaymentProviderRequest request, String status, String code, String message) {
        if (recordStore != null) {
            recordStore.recordPaymentTransaction(
                    txId,
                    request.getPaymentReference(),
                    null,
                    request.getAmount(),
                    request.getCurrency(),
                    status,
                    code,
                    message
            );
        }
    }

    private void recordRefund(String refundTxId, PaymentProviderRefundRequest request, String status, String code, String message) {
        if (recordStore != null) {
            recordStore.recordRefundTransaction(
                    refundTxId,
                    request.getProviderTransactionId(),
                    request.getRefundReference(),
                    null,
                    request.getAmount(),
                    request.getCurrency(),
                    status,
                    code,
                    message
            );
        }
    }

    private String resolvePaymentScenario(PaymentProviderRequest request) {
        if (request.getSimulationScenario() != null && !request.getSimulationScenario().isBlank()) {
            return request.getSimulationScenario().trim();
        }

        String description = request.getDescription();
        if (description != null) {
            if (description.contains("[SIM_FAIL]")) {
                return "FAIL";
            }
            if (description.contains("[SIM_TIMEOUT]")) {
                return "TIMEOUT";
            }
            if (description.contains("[SIM_ACTION]")) {
                return "REQUIRES_ACTION";
            }
        }

        return "SUCCESS";
    }

    private String resolveRefundScenario(PaymentProviderRefundRequest request) {
        if (request.getSimulationScenario() != null && !request.getSimulationScenario().isBlank()) {
            return request.getSimulationScenario().trim();
        }

        String reason = request.getReason();
        if (reason != null) {
            if (reason.contains("[SIM_FAIL]")) {
                return "FAIL";
            }
            if (reason.contains("[SIM_TIMEOUT]")) {
                return "TIMEOUT";
            }
        }

        if (request.getAmount() != null) {
            if (request.getAmount().compareTo(new BigDecimal("999.00")) == 0 || request.getAmount().compareTo(new BigDecimal("999.99")) == 0) {
                return "FAIL";
            }
            if (request.getAmount().compareTo(new BigDecimal("998.00")) == 0 || request.getAmount().compareTo(new BigDecimal("998.99")) == 0) {
                return "TIMEOUT";
            }
        }

        return "SUCCESS";
    }
}
