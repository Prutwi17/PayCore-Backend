package com.paycore.payment.provider;

import com.paycore.payment.exception.PaymentProviderException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory and registry for resolving payment provider implementations.
 */
@Component
public class PaymentProviderFactory {

    private final Map<String, PaymentProvider> providers = new ConcurrentHashMap<>();

    public PaymentProviderFactory(List<PaymentProvider> providerList) {
        for (PaymentProvider provider : providerList) {
            providers.put(provider.getProviderName().toUpperCase(), provider);
        }
    }

    public PaymentProvider getProvider(String providerName) {
        String key = (providerName != null && !providerName.isBlank()) ? providerName.toUpperCase() : "SIMULATED";
        PaymentProvider provider = providers.get(key);
        if (provider == null) {
            throw new PaymentProviderException("Unsupported payment provider: " + providerName);
        }
        return provider;
    }

    public PaymentProvider getDefaultProvider() {
        return getProvider("SIMULATED");
    }
}
