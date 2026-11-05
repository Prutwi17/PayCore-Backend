package com.paycore.transaction.repository;

import com.paycore.transaction.dto.TransactionType;
import com.paycore.transaction.entity.TransactionView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface TransactionViewRepository extends JpaRepository<TransactionView, String> {

    @Query("SELECT t FROM TransactionView t WHERE (:merchantId IS NULL OR t.merchantId = :merchantId) AND " +
            "(:type IS NULL OR t.transactionType = :type) AND " +
            "(:status IS NULL OR t.status = :status) AND " +
            "(:search IS NULL OR LOWER(t.transactionReference) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(t.paymentReference) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
            "(:startDate IS NULL OR t.createdAt >= :startDate) AND " +
            "(:endDate IS NULL OR t.createdAt <= :endDate)")
    Page<TransactionView> searchTransactions(
            @Param("merchantId") Long merchantId,
            @Param("type") TransactionType type,
            @Param("status") String status,
            @Param("search") String search,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );
}
