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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook Endpoint Service Unit Tests")
class WebhookEndpointServiceTest {

    @Mock
    private WebhookEndpointRepository endpointRepository;

    @Mock
    private WebhookSsrfValidator ssrfValidator;

    private WebhookSigner webhookSigner;
    private WebhookEndpointService endpointService;

    @BeforeEach
    void setUp() {
        webhookSigner = new WebhookSigner();
        endpointService = new WebhookEndpointService(endpointRepository, ssrfValidator, webhookSigner);
    }

    @Test
    @DisplayName("Create endpoint validates SSRF, hashes secret, and returns secret ONCE")
    void testCreateEndpointSuccess() {
        WebhookEndpointCreateRequest request = new WebhookEndpointCreateRequest(
                "https://api.merchant.com/webhook",
                "PAYMENT_SUCCESS,PAYMENT_FAILED",
                "Primary Webhook"
        );

        when(endpointRepository.existsByMerchantIdAndUrl(10L, request.getUrl())).thenReturn(false);
        when(endpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> {
            WebhookEndpoint ep = invocation.getArgument(0);
            ep.setEndpointReference("ep_test_12345");
            return ep;
        });

        WebhookEndpointResponseDto response = endpointService.createEndpoint(10L, request);

        assertNotNull(response);
        assertEquals("https://api.merchant.com/webhook", response.getUrl());
        assertEquals("PAYMENT_SUCCESS,PAYMENT_FAILED", response.getSubscribedEvents());
        assertEquals(EndpointStatus.ACTIVE, response.getStatus());
        // Plaintext secret must be returned upon creation
        assertNotNull(response.getSecret());
        assertTrue(response.getSecret().startsWith("whsec_"));

        verify(ssrfValidator).validateUrl("https://api.merchant.com/webhook");

        ArgumentCaptor<WebhookEndpoint> captor = ArgumentCaptor.forClass(WebhookEndpoint.class);
        verify(endpointRepository).save(captor.capture());
        WebhookEndpoint saved = captor.getValue();
        assertNotNull(saved.getSecretHash());
        assertNotNull(saved.getSecretEncrypted());
    }

    @Test
    @DisplayName("Create endpoint with duplicate URL for same merchant throws DuplicateResourceException")
    void testDuplicateUrlThrows() {
        WebhookEndpointCreateRequest request = new WebhookEndpointCreateRequest(
                "https://api.merchant.com/webhook", "*", "Duplicate"
        );
        when(endpointRepository.existsByMerchantIdAndUrl(10L, request.getUrl())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> endpointService.createEndpoint(10L, request));
    }

    @Test
    @DisplayName("Rotate secret generates new secret, updates database, and returns secret ONCE")
    void testRotateSecret() {
        WebhookEndpoint existing = new WebhookEndpoint();
        existing.setId(1L);
        existing.setEndpointReference("ep_rotate_01");
        existing.setMerchantId(10L);
        existing.setUrl("https://api.merchant.com/webhook");
        existing.setSecretHash("old_hash");
        existing.setSecretEncrypted("old_enc");

        when(endpointRepository.findByEndpointReferenceAndMerchantId("ep_rotate_01", 10L))
                .thenReturn(Optional.of(existing));
        when(endpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WebhookEndpointResponseDto rotated = endpointService.rotateSecret("ep_rotate_01", 10L);

        assertNotNull(rotated.getSecret());
        assertTrue(rotated.getSecret().startsWith("whsec_"));
        assertNotEquals("old_hash", existing.getSecretHash());
        assertNotEquals("old_enc", existing.getSecretEncrypted());
    }

    @Test
    @DisplayName("List endpoints returns endpoints with secret masked and plaintext secret null")
    void testListEndpointsNeverExposesSecrets() {
        WebhookEndpoint ep = new WebhookEndpoint();
        ep.setId(1L);
        ep.setEndpointReference("ep_list_01");
        ep.setMerchantId(10L);
        ep.setUrl("https://api.merchant.com/webhook");
        ep.setSecretPrefix("whsec_");
        ep.setSecretEncrypted("enc_secret");

        when(endpointRepository.findByMerchantId(10L)).thenReturn(List.of(ep));

        List<WebhookEndpointResponseDto> list = endpointService.listEndpoints(10L);
        assertEquals(1, list.size());
        assertNull(list.get(0).getSecret(), "Secret must be null in list queries to prevent exposure");
        assertEquals("whsec_********************************", list.get(0).getMaskedSecret());
    }

    @Test
    @DisplayName("Delete endpoint calls repository delete")
    void testDeleteEndpoint() {
        WebhookEndpoint ep = new WebhookEndpoint();
        ep.setEndpointReference("ep_del_01");
        ep.setMerchantId(10L);

        when(endpointRepository.findByEndpointReferenceAndMerchantId("ep_del_01", 10L))
                .thenReturn(Optional.of(ep));

        endpointService.deleteEndpoint("ep_del_01", 10L);
        verify(endpointRepository).delete(ep);
    }
}
