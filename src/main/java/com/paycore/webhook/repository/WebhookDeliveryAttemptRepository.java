package com.paycore.webhook.repository;

import com.paycore.webhook.entity.WebhookDeliveryAttempt;
import com.paycore.webhook.entity.WebhookDeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookDeliveryAttemptRepository extends JpaRepository<WebhookDeliveryAttempt, Long> {

    Optional<WebhookDeliveryAttempt> findByAttemptReference(String attemptReference);

    Optional<WebhookDeliveryAttempt> findByAttemptReferenceAndMerchantId(String attemptReference, Long merchantId);

    Page<WebhookDeliveryAttempt> findByMerchantId(Long merchantId, Pageable pageable);

    Page<WebhookDeliveryAttempt> findByMerchantIdAndDeliveryStatus(Long merchantId, WebhookDeliveryStatus status, Pageable pageable);

    @Query("SELECT d FROM WebhookDeliveryAttempt d WHERE d.deliveryStatus = 'RETRYING' AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now) ORDER BY d.nextRetryAt ASC")
    List<WebhookDeliveryAttempt> findDueRetries(@Param("now") Instant now, Pageable pageable);

    Page<WebhookDeliveryAttempt> findByDeliveryStatus(WebhookDeliveryStatus status, Pageable pageable);

    @Query("SELECT COUNT(d) FROM WebhookDeliveryAttempt d WHERE d.deliveryStatus = 'SUCCESS'")
    long countSuccessfulDeliveries();

    @Query("SELECT COUNT(d) FROM WebhookDeliveryAttempt d WHERE d.deliveryStatus = 'RETRYING'")
    long countRetryingDeliveries();

    @Query("SELECT COUNT(d) FROM WebhookDeliveryAttempt d WHERE d.deliveryStatus = 'DEAD_LETTER'")
    long countDeadLetterDeliveries();

    @Query("SELECT COUNT(d) FROM WebhookDeliveryAttempt d WHERE d.deliveryStatus IN ('FAILED', 'DEAD_LETTER')")
    long countFailedDeliveries();
}
