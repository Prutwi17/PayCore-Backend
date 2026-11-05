package com.paycore.webhook.repository;

import com.paycore.webhook.entity.EndpointStatus;
import com.paycore.webhook.entity.WebhookEndpoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {

    Optional<WebhookEndpoint> findByEndpointReference(String endpointReference);

    Optional<WebhookEndpoint> findByEndpointReferenceAndMerchantId(String endpointReference, Long merchantId);

    List<WebhookEndpoint> findByMerchantId(Long merchantId);

    List<WebhookEndpoint> findByMerchantIdAndStatus(Long merchantId, EndpointStatus status);

    Page<WebhookEndpoint> findByMerchantId(Long merchantId, Pageable pageable);

    boolean existsByMerchantIdAndUrl(Long merchantId, String url);
}
