package com.paycore.reconciliation.adapter;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Adapter querying the independent statement store of the simulated payment provider.
 */
@Component
public class SimulatedProviderReconciliationAdapter implements ProviderReconciliationAdapter {

    private final SimulatedProviderRecordStore recordStore;

    public SimulatedProviderReconciliationAdapter(SimulatedProviderRecordStore recordStore) {
        this.recordStore = recordStore;
    }

    @Override
    public String getProviderName() {
        return "SIMULATED";
    }

    @Override
    public List<ProviderTransactionRecord> fetchTransactionRecords(Instant from, Instant to, Long merchantId) {
        return recordStore.queryTransactions(from, to, merchantId);
    }

    @Override
    public List<ProviderRefundRecord> fetchRefundRecords(Instant from, Instant to, Long merchantId) {
        return recordStore.queryRefunds(from, to, merchantId);
    }
}
