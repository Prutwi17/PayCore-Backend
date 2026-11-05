package com.paycore.webhook.repository;

import com.paycore.webhook.entity.WebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findByEventReference(String eventReference);

    Optional<WebhookEvent> findByEventReferenceAndMerchantId(String eventReference, Long merchantId);

    Page<WebhookEvent> findByMerchantId(Long merchantId, Pageable pageable);

    Page<WebhookEvent> findByMerchantIdAndEventType(Long merchantId, String eventType, Pageable pageable);
}
