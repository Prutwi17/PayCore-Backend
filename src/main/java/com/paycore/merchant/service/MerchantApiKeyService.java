package com.paycore.merchant.service;

import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.common.util.HashUtil;
import com.paycore.merchant.dto.ApiKeyCreateRequest;
import com.paycore.merchant.dto.ApiKeyCreatedResponseDto;
import com.paycore.merchant.dto.ApiKeyResponseDto;
import com.paycore.merchant.entity.MerchantApiKey;
import com.paycore.merchant.repository.MerchantApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MerchantApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(MerchantApiKeyService.class);

    private final MerchantApiKeyRepository apiKeyRepository;

    public MerchantApiKeyService(MerchantApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Transactional
    public ApiKeyCreatedResponseDto generateApiKey(Long merchantId, ApiKeyCreateRequest request) {
        if (merchantId == null) {
            throw new ResourceNotFoundException("Authenticated user has no associated merchant account");
        }

        String typePrefix = "SECRET".equalsIgnoreCase(request.getKeyType()) ? "sk_" : "pk_";
        String envPrefix = "LIVE".equalsIgnoreCase(request.getEnvironment()) ? "live_" : "test_";
        String prefixTag = typePrefix + envPrefix; // e.g., sk_live_ or sk_test_

        // Generate high-entropy 256-bit random hex secret
        String randomHex = HashUtil.generateSecureToken(24);
        String fullSecretKey = prefixTag + randomHex;

        // Key prefix for display: prefixTag + first 8 characters
        String keyPrefix = prefixTag + randomHex.substring(0, 8);

        // Compute secure salted SHA-256 hash for storage
        String keyHash = HashUtil.sha256Hex(fullSecretKey);

        MerchantApiKey apiKey = new MerchantApiKey();
        apiKey.setMerchantId(merchantId);
        apiKey.setKeyPrefix(keyPrefix);
        apiKey.setKeyHash(keyHash);
        apiKey.setKeyType(request.getKeyType().toUpperCase());
        apiKey.setEnvironment(request.getEnvironment().toUpperCase());
        apiKey.setStatus("ACTIVE");
        apiKey.setCreatedAt(Instant.now());

        MerchantApiKey saved = apiKeyRepository.save(apiKey);
        log.info("Generated new [{}] API key with prefix [{}] for merchant [{}]", saved.getKeyType(), keyPrefix, merchantId);

        // Plain secretKey is returned strictly once upon generation
        return new ApiKeyCreatedResponseDto(
                saved.getId(),
                saved.getKeyPrefix(),
                fullSecretKey,
                saved.getKeyType(),
                saved.getEnvironment(),
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponseDto> listApiKeys(Long merchantId) {
        if (merchantId == null) {
            throw new ResourceNotFoundException("Authenticated user has no associated merchant account");
        }
        return apiKeyRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)
                .stream()
                .map(ApiKeyResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public ApiKeyResponseDto revokeApiKey(Long keyId, Long merchantId) {
        if (merchantId == null) {
            throw new ResourceNotFoundException("Authenticated user has no associated merchant account");
        }

        // Ownership enforcement: Key must match both ID and authenticated merchantId
        MerchantApiKey key = apiKeyRepository.findByIdAndMerchantId(keyId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("API Key not found or does not belong to merchant: " + keyId));

        key.setStatus("REVOKED");
        key.setRevokedAt(Instant.now());

        MerchantApiKey saved = apiKeyRepository.save(key);
        log.info("Revoked API key [{}] for merchant [{}]", key.getKeyPrefix(), merchantId);
        return ApiKeyResponseDto.fromEntity(saved);
    }
}
