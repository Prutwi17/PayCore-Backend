package com.paycore.refund.repository;

import com.paycore.refund.entity.Refund;
import com.paycore.refund.entity.RefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByRefundReference(String refundReference);

    Optional<Refund> findByRefundReferenceAndMerchantId(String refundReference, Long merchantId);

    @Query("SELECT r FROM Refund r LEFT JOIN FETCH r.payment p LEFT JOIN FETCH r.merchant m WHERE r.refundReference = :refundReference")
    Optional<Refund> findByRefundReferenceWithDetails(@Param("refundReference") String refundReference);

    @Query("SELECT r FROM Refund r LEFT JOIN FETCH r.payment p LEFT JOIN FETCH r.merchant m WHERE r.refundReference = :refundReference AND r.merchant.id = :merchantId")
    Optional<Refund> findByRefundReferenceAndMerchantIdWithDetails(
            @Param("refundReference") String refundReference,
            @Param("merchantId") Long merchantId
    );

    @Query("SELECT r FROM Refund r LEFT JOIN FETCH r.payment p LEFT JOIN FETCH r.merchant m WHERE p.paymentReference = :paymentReference ORDER BY r.createdAt DESC")
    List<Refund> findByPaymentReferenceOrderByCreatedAtDesc(@Param("paymentReference") String paymentReference);

    @Query("SELECT r FROM Refund r LEFT JOIN FETCH r.payment p LEFT JOIN FETCH r.merchant m WHERE p.paymentReference = :paymentReference AND r.merchant.id = :merchantId ORDER BY r.createdAt DESC")
    List<Refund> findByPaymentReferenceAndMerchantIdOrderByCreatedAtDesc(
            @Param("paymentReference") String paymentReference,
            @Param("merchantId") Long merchantId
    );

    @Query("SELECT r FROM Refund r LEFT JOIN FETCH r.payment p LEFT JOIN FETCH r.merchant m WHERE r.merchant.id = :merchantId AND " +
            "(:status IS NULL OR r.status = :status) AND " +
            "(:search IS NULL OR LOWER(r.refundReference) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(p.paymentReference) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Refund> searchMerchantRefunds(
            @Param("merchantId") Long merchantId,
            @Param("status") RefundStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT r FROM Refund r LEFT JOIN FETCH r.payment p LEFT JOIN FETCH r.merchant m WHERE " +
            "(:status IS NULL OR r.status = :status) AND " +
            "(:merchantId IS NULL OR r.merchant.id = :merchantId) AND " +
            "(:search IS NULL OR LOWER(r.refundReference) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(p.paymentReference) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(m.merchantCode) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Refund> searchAdminRefunds(
            @Param("status") RefundStatus status,
            @Param("merchantId") Long merchantId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
        SELECT r FROM Refund r
        WHERE (:startDate IS NULL OR r.createdAt >= :startDate)
          AND (:endDate IS NULL OR r.createdAt <= :endDate)
        ORDER BY r.createdAt ASC
    """)
    List<Refund> findByDateRange(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("""
        SELECT COUNT(r) FROM Refund r
        WHERE (:startDate IS NULL OR r.createdAt >= :startDate)
          AND (:endDate IS NULL OR r.createdAt <= :endDate)
    """)
    long countRefundsBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("""
        SELECT COUNT(r) FROM Refund r
        WHERE r.status = :status
          AND (:startDate IS NULL OR r.createdAt >= :startDate)
          AND (:endDate IS NULL OR r.createdAt <= :endDate)
    """)
    long countByStatusBetween(@Param("status") RefundStatus status, @Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("""
        SELECT COUNT(r) FROM Refund r
        WHERE r.status IN :statuses
          AND (:startDate IS NULL OR r.createdAt >= :startDate)
          AND (:endDate IS NULL OR r.createdAt <= :endDate)
    """)
    long countByStatusInBetween(@Param("statuses") List<RefundStatus> statuses, @Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("""
        SELECT COALESCE(SUM(r.amount), 0) FROM Refund r
        WHERE r.status = 'SUCCESS'
          AND (:startDate IS NULL OR r.createdAt >= :startDate)
          AND (:endDate IS NULL OR r.createdAt <= :endDate)
    """)
    BigDecimal sumSuccessfulAmountBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
}
