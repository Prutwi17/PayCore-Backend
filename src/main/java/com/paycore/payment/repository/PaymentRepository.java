package com.paycore.payment.repository;

import com.paycore.payment.entity.Payment;
import com.paycore.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.merchant WHERE p.id = :id")
    Optional<Payment> findByIdWithMerchant(@Param("id") Long id);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.merchant WHERE p.paymentReference = :paymentReference")
    Optional<Payment> findByPaymentReferenceWithMerchant(@Param("paymentReference") String paymentReference);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.merchant WHERE p.paymentReference = :paymentReference AND p.merchant.id = :merchantId")
    Optional<Payment> findByPaymentReferenceAndMerchantIdWithMerchant(
            @Param("paymentReference") String paymentReference,
            @Param("merchantId") Long merchantId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentReference = :paymentReference")
    Optional<Payment> findByPaymentReferenceForUpdate(@Param("paymentReference") String paymentReference);

    Optional<Payment> findByPaymentReference(String paymentReference);

    Optional<Payment> findByPaymentReferenceAndMerchantId(String paymentReference, Long merchantId);

    Page<Payment> findByMerchantId(Long merchantId, Pageable pageable);

    long countByMerchantId(Long merchantId);

    long countByMerchantIdAndStatus(Long merchantId, PaymentStatus status);

    long countByMerchantIdAndStatusIn(Long merchantId, List<PaymentStatus> statuses);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.merchant.id = :merchantId AND p.status = 'SUCCESS'")
    BigDecimal sumSuccessfulAmountByMerchantId(@Param("merchantId") Long merchantId);

    Page<Payment> findByMerchantIdAndStatus(Long merchantId, PaymentStatus status, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.merchant WHERE p.merchant.id = :merchantId AND " +
            "(:status IS NULL OR p.status = :status) AND " +
            "(:search IS NULL OR LOWER(p.paymentReference) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(p.customerReference) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Payment> searchMerchantPayments(
            @Param("merchantId") Long merchantId,
            @Param("status") PaymentStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.merchant WHERE " +
            "(:status IS NULL OR p.status = :status) AND " +
            "(:merchantId IS NULL OR p.merchant.id = :merchantId) AND " +
            "(:search IS NULL OR LOWER(p.paymentReference) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(p.merchant.merchantCode) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Payment> searchAdminPayments(
            @Param("status") PaymentStatus status,
            @Param("merchantId") Long merchantId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
        SELECT p FROM Payment p
        WHERE (:startDate IS NULL OR p.createdAt >= :startDate)
          AND (:endDate IS NULL OR p.createdAt <= :endDate)
        ORDER BY p.createdAt ASC
    """)
    List<Payment> findByDateRange(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("""
        SELECT COUNT(p) FROM Payment p
        WHERE (:startDate IS NULL OR p.createdAt >= :startDate)
          AND (:endDate IS NULL OR p.createdAt <= :endDate)
    """)
    long countPaymentsBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("""
        SELECT COUNT(p) FROM Payment p
        WHERE p.status = :status
          AND (:startDate IS NULL OR p.createdAt >= :startDate)
          AND (:endDate IS NULL OR p.createdAt <= :endDate)
    """)
    long countByStatusBetween(@Param("status") PaymentStatus status, @Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("""
        SELECT COUNT(p) FROM Payment p
        WHERE p.status IN :statuses
          AND (:startDate IS NULL OR p.createdAt >= :startDate)
          AND (:endDate IS NULL OR p.createdAt <= :endDate)
    """)
    long countByStatusInBetween(@Param("statuses") List<PaymentStatus> statuses, @Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
        WHERE p.status = 'SUCCESS'
          AND (:startDate IS NULL OR p.createdAt >= :startDate)
          AND (:endDate IS NULL OR p.createdAt <= :endDate)
    """)
    BigDecimal sumSuccessfulAmountBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("""
        SELECT p.status, COUNT(p) FROM Payment p
        WHERE (:startDate IS NULL OR p.createdAt >= :startDate)
          AND (:endDate IS NULL OR p.createdAt <= :endDate)
        GROUP BY p.status
    """)
    List<Object[]> countGroupedByStatusBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
}
