package com.paycore.transaction.service;

import com.paycore.transaction.dto.TransactionItemDto;
import com.paycore.transaction.dto.TransactionType;
import com.paycore.transaction.entity.TransactionView;
import com.paycore.transaction.repository.TransactionViewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class TransactionHistoryService {

    private final TransactionViewRepository transactionViewRepository;

    public TransactionHistoryService(TransactionViewRepository transactionViewRepository) {
        this.transactionViewRepository = transactionViewRepository;
    }

    public Page<TransactionItemDto> searchMerchantTransactions(
            Long merchantId,
            TransactionType type,
            String status,
            String search,
            Instant startDate,
            Instant endDate,
            Pageable pageable
    ) {
        if (merchantId == null) {
            throw new IllegalArgumentException("Merchant ID must not be null for merchant transaction search");
        }
        return transactionViewRepository.searchTransactions(
                merchantId, type, status, search, startDate, endDate, pageable
        ).map(TransactionView::toDto);
    }

    public Page<TransactionItemDto> searchAdminTransactions(
            Long merchantId,
            TransactionType type,
            String status,
            String search,
            Instant startDate,
            Instant endDate,
            Pageable pageable
    ) {
        return transactionViewRepository.searchTransactions(
                merchantId, type, status, search, startDate, endDate, pageable
        ).map(TransactionView::toDto);
    }
}
