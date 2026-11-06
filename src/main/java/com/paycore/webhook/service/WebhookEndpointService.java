package com.paycore.webhook.service;

import com.paycore.common.exception.DuplicateResourceException;
import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.webhook.dto.WebhookEndpointCreateRequest;
import com.paycore.webhook.dto.WebhookEndpointResponseDto;
import com.paycore.webhook.dto.WebhookEndpointUpdateRequest;
import com.paycore.webhook.entity.EndpointStatus;
import com.paycore.webhook.entity.WebhookEndpoint;
import com.paycore.webhook.repository.WebhookEndpointRepository;
import com.paycore.webhook.security.WebhookSigner;
import com.paycore.webhook.security.WebhookSsrfValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WebhookEndpointService {

    private static final Logger log = LoggerFactory.getLogger(WebhookEndpointService.class);

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookSsrfValidator webhookSsrfValidator;
    private final WebhookSigner webhookSigner;

    public WebhookEndpointService(WebhookEndpointRepository webhookEndpointRepository,
                                  WebhookSsrfValidator webhookSsrfValidator,
                                  WebhookSigner webhookSigner) {
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.webhookSsrfValidator = webhookSsrfValidator;
        this.webhookSigner = webhookSigner;
    }

    @Transactional
    public WebhookEndpointResponseDto createEndpoint(Long merchantId, WebhookEndpointCreateRequest request) {
        // Validate destination URL against SSRF and DNS rebinding
        webhookSsrfValidator.validateUrl(request.getUrl());

        if (webhookEndpointRepository.existsByMerchantIdAndUrl(merchantId, request.getUrl().trim())) {
            throw new DuplicateResourceException("An endpoint with this URL already exists for this merchant");
        }

        String rawSecret = webhookSigner.generateSecret();
        String secretHash = webhookSigner.hashSecret(rawSecret);
        String secretEncrypted = Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8));

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setEndpointReference("ep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        endpoint.setMerchantId(merchantId);
        endpoint.setUrl(request.getUrl().trim());
        endpoint.setSecretPrefix(WebhookSigner.SECRET_PREFIX);
        endpoint.setSecretHash(secretHash);
        endpoint.setSecretEncrypted(secretEncrypted);
        endpoint.setSubscribedEvents(request.getSubscribedEvents() != null && !request.getSubscribedEvents().isBlank() ? request.getSubscribedEvents().trim() : "*");
        endpoint.setStatus(EndpointStatus.ACTIVE);
        endpoint.setDescription(request.getDescription());
        endpoint.setCreatedAt(Instant.now());
        endpoint.setUpdatedAt(Instant.now());

        WebhookEndpoint saved = webhookEndpointRepository.save(endpoint);
        log.info("Registered new webhook endpoint [ref={}, merchantId={}, url={}]", saved.getEndpointReference(), merchantId, saved.getUrl());

        // Return plaintext secret ONLY in creation response!
        return WebhookEndpointResponseDto.fromEntity(saved, rawSecret);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponseDto> listEndpoints(Long merchantId) {
        return webhookEndpointRepository.findByMerchantId(merchantId).stream()
                .map(ep -> WebhookEndpointResponseDto.fromEntity(ep, null))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<WebhookEndpointResponseDto> listEndpointsPaged(Long merchantId, Pageable pageable) {
        return webhookEndpointRepository.findByMerchantId(merchantId, pageable)
                .map(ep -> WebhookEndpointResponseDto.fromEntity(ep, null));
    }

    @Transactional(readOnly = true)
    public WebhookEndpointResponseDto getEndpoint(String endpointReference, Long merchantId) {
        WebhookEndpoint endpoint = webhookEndpointRepository.findByEndpointReferenceAndMerchantId(endpointReference, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + endpointReference));
        return WebhookEndpointResponseDto.fromEntity(endpoint, null);
    }

    @Transactional
    public WebhookEndpointResponseDto updateEndpoint(String endpointReference, Long merchantId, WebhookEndpointUpdateRequest request) {
        WebhookEndpoint endpoint = webhookEndpointRepository.findByEndpointReferenceAndMerchantId(endpointReference, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + endpointReference));

        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            webhookSsrfValidator.validateUrl(request.getUrl());
            endpoint.setUrl(request.getUrl().trim());
        }
        if (request.getSubscribedEvents() != null) {
            endpoint.setSubscribedEvents(request.getSubscribedEvents().trim());
        }
        if (request.getDescription() != null) {
            endpoint.setDescription(request.getDescription().trim());
        }
        if (request.getStatus() != null) {
            endpoint.setStatus(request.getStatus());
        }

        endpoint.setUpdatedAt(Instant.now());
        WebhookEndpoint updated = webhookEndpointRepository.save(endpoint);
        log.info("Updated webhook endpoint [ref={}, merchantId={}]", endpointReference, merchantId);

        return WebhookEndpointResponseDto.fromEntity(updated, null);
    }

    @Transactional
    public WebhookEndpointResponseDto rotateSecret(String endpointReference, Long merchantId) {
        WebhookEndpoint endpoint = webhookEndpointRepository.findByEndpointReferenceAndMerchantId(endpointReference, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + endpointReference));

        String newRawSecret = webhookSigner.generateSecret();
        String secretHash = webhookSigner.hashSecret(newRawSecret);
        String secretEncrypted = Base64.getEncoder().encodeToString(newRawSecret.getBytes(StandardCharsets.UTF_8));

        endpoint.setSecretHash(secretHash);
        endpoint.setSecretEncrypted(secretEncrypted);
        endpoint.setUpdatedAt(Instant.now());

        WebhookEndpoint updated = webhookEndpointRepository.save(endpoint);
        log.info("Rotated signing secret for webhook endpoint [ref={}, merchantId={}]", endpointReference, merchantId);

        // Return new plaintext secret ONCE
        return WebhookEndpointResponseDto.fromEntity(updated, newRawSecret);
    }

    @Transactional
    public void deleteEndpoint(String endpointReference, Long merchantId) {
        WebhookEndpoint endpoint = webhookEndpointRepository.findByEndpointReferenceAndMerchantId(endpointReference, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + endpointReference));
        webhookEndpointRepository.delete(endpoint);
        log.info("Deleted webhook endpoint [ref={}, merchantId={}]", endpointReference, merchantId);
    }

    public String resolvePlainSecret(WebhookEndpoint endpoint) {
        if (endpoint.getSecretEncrypted() == null) {
            return null;
        }
        return new String(Base64.getDecoder().decode(endpoint.getSecretEncrypted()), StandardCharsets.UTF_8);
    }
}
