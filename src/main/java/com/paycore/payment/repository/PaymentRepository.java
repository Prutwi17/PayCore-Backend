package com.paycore.payment.repository;

import com.paycore.payment.entity.Payment;
import com.paycore.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    Optional<Payment> findByPaymentReference(String paymentReference);

    Optional<Payment> findByPaymentReferenceAndMerchantId(String paymentReference, Long merchantId);

    Page<Payment> findByMerchantId(Long merchantId, Pageable pageable);

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
}
