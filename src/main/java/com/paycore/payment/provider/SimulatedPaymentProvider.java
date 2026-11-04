package com.paycore.payment.provider;

import com.paycore.payment.exception.PaymentProviderTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Deterministic simulated payment provider for local development, automated testing,
 * and sandbox demonstrations.
 */
@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentProvider.class);
    public static final String PROVIDER_NAME = "SIMULATED";

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public PaymentProviderResponse initiatePayment(PaymentProviderRequest request) {
        String txId = "sim_tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String scenario = resolveScenario(request);

        log.info("SimulatedPaymentProvider processing payment reference [{}] with scenario [{}]",
                request.getPaymentReference(), scenario);

        switch (scenario.toUpperCase()) {
            case "FAIL":
            case "DECLINE":
                return PaymentProviderResponse.failed(
                        txId,
                        "DECLINED_INSUFFICIENT_FUNDS",
                        "The simulated card issuer reported insufficient account funds."
                );

            case "REQUIRES_ACTION":
            case "3DS":
                return PaymentProviderResponse.requiresAction(
                        txId,
                        "https://sandbox.paycore.internal/3ds/verify?tx=" + txId,
                        "Customer 3D-Secure authentication required."
                );

            case "TIMEOUT":
            case "UNKNOWN":
                log.warn("SimulatedPaymentProvider simulating downstream socket read timeout for tx [{}]", txId);
                throw new PaymentProviderTimeoutException("Downstream simulated provider timed out after 10000ms. Status unconfirmed.");

            case "SUCCESS":
            default:
                return PaymentProviderResponse.success(
                        txId,
                        "200_APPROVED",
                        "Payment authorized and settled successfully by simulated provider."
                );
        }
    }

    @Override
    public PaymentProviderResponse queryPaymentStatus(String providerTransactionId, String requestReference) {
        log.info("SimulatedPaymentProvider querying status for provider tx [{}]", providerTransactionId);
        // By default in simulation, querying an existing transaction confirms successful settlement
        return PaymentProviderResponse.success(
                providerTransactionId,
                "200_CONFIRMED",
                "Transaction status confirmed as SUCCESS by simulated network."
        );
    }

    @Override
    public PaymentProviderResponse cancelPayment(String providerTransactionId, String requestReference) {
        log.info("SimulatedPaymentProvider cancelling transaction [{}]", providerTransactionId);
        return PaymentProviderResponse.cancelled(
                providerTransactionId,
                "Authorization hold successfully voided by simulated provider."
        );
    }

    private String resolveScenario(PaymentProviderRequest request) {
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
}
