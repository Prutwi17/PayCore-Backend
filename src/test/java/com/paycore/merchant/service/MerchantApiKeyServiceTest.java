package com.paycore.merchant.service;

import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.merchant.dto.ApiKeyCreateRequest;
import com.paycore.merchant.dto.ApiKeyCreatedResponseDto;
import com.paycore.merchant.dto.ApiKeyResponseDto;
import com.paycore.merchant.entity.MerchantApiKey;
import com.paycore.merchant.repository.MerchantApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantApiKeyServiceTest {

    @Mock
    private MerchantApiKeyRepository apiKeyRepository;

    private MerchantApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new MerchantApiKeyService(apiKeyRepository);
    }

    @Test
    @DisplayName("Generate API key should return plain secret key once and store salted hash")
    void testGenerateApiKey() {
        ApiKeyCreateRequest request = new ApiKeyCreateRequest("SECRET", "LIVE");

        when(apiKeyRepository.save(any(MerchantApiKey.class))).thenAnswer(invocation -> {
            MerchantApiKey entity = invocation.getArgument(0);
            entity.setId(101L);
            return entity;
        });

        ApiKeyCreatedResponseDto result = apiKeyService.generateApiKey(10L, request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getSecretKey()).startsWith("sk_live_");
        assertThat(result.getKeyPrefix()).startsWith("sk_live_");
        assertThat(result.getWarningMessage()).contains("cannot be displayed or retrieved again");

        // Verify entity stored has SHA-256 hash, not plain secret
        verify(apiKeyRepository).save(argThat(entity ->
                entity.getKeyHash() != null &&
                !entity.getKeyHash().equals(result.getSecretKey()) &&
                entity.getKeyHash().length() == 64 // 64 hex chars for SHA-256
        ));
    }

    @Test
    @DisplayName("List API keys should return metadata only and never reveal secret key or hash")
    void testListApiKeys() {
        MerchantApiKey key = new MerchantApiKey(10L, "sk_live_3f8a9e1d", "sha256HashHidden", "SECRET", "LIVE");
        key.setId(1L);

        when(apiKeyRepository.findByMerchantIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(key));

        List<ApiKeyResponseDto> list = apiKeyService.listApiKeys(10L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getKeyPrefix()).isEqualTo("sk_live_3f8a9e1d");
        assertThat(list.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Revoking API key should update status to REVOKED and timestamp revokedAt")
    void testRevokeApiKey() {
        MerchantApiKey key = new MerchantApiKey(10L, "sk_live_3f8a9e1d", "hash", "SECRET", "LIVE");
        key.setId(5L);

        when(apiKeyRepository.findByIdAndMerchantId(5L, 10L)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any(MerchantApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyResponseDto revoked = apiKeyService.revokeApiKey(5L, 10L);

        assertThat(revoked.getStatus()).isEqualTo("REVOKED");
        assertThat(revoked.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("Revoking API key belonging to another merchant should throw ResourceNotFoundException")
    void testRevokeCrossMerchantKeyFails() {
        when(apiKeyRepository.findByIdAndMerchantId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revokeApiKey(99L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("API Key not found or does not belong to merchant");

        verify(apiKeyRepository, never()).save(any());
    }
}
