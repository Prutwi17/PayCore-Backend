package com.paycore.payment.idempotency.repository;

import com.paycore.payment.idempotency.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Query("SELECT r FROM IdempotencyRecord r LEFT JOIN FETCH r.merchant WHERE r.merchant.id = :merchantId AND r.idempotencyKey = :idempotencyKey")
    Optional<IdempotencyRecord> findByMerchantIdAndIdempotencyKeyWithMerchant(
            @Param("merchantId") Long merchantId,
            @Param("idempotencyKey") String idempotencyKey
    );

    Optional<IdempotencyRecord> findByMerchantIdAndIdempotencyKey(Long merchantId, String idempotencyKey);
}
