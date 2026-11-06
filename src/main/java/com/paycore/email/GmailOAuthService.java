package com.paycore.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.email.exception.EmailDeliveryFailedException;
import com.paycore.email.exception.EmailDeliveryUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class GmailOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GmailOAuthService.class);
    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";

    private final EmailProperties emailProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // In-memory cache for the short-lived access token
    private final AtomicReference<CachedAccessToken> cachedToken = new AtomicReference<>();

    public GmailOAuthService(EmailProperties emailProperties) {
        this.emailProperties = emailProperties;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public static class CachedAccessToken {
        private final String accessToken;
        private final Instant expiresAt;

        public CachedAccessToken(String accessToken, Instant expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }

        public boolean isValid() {
            return accessToken != null && Instant.now().isBefore(expiresAt);
        }

        public String getAccessToken() {
            return accessToken;
        }
    }

    public static class TokenExchangeResult {
        private final String accessToken;
        private final String refreshToken;
        private final int expiresIn;

        public TokenExchangeResult(String accessToken, String refreshToken, int expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public int getExpiresIn() {
            return expiresIn;
        }
    }

    /**
     * Builds the Google OAuth 2.0 Consent Screen authorization URL for the Gmail sender account.
     */
    public String buildAuthorizationUrl(String state) {
        if (emailProperties.getClientId() == null || emailProperties.getClientId().isBlank()) {
            throw new EmailDeliveryUnavailableException("Google Client ID is not configured.");
        }

        return UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_URL)
                .queryParam("client_id", emailProperties.getClientId())
                .queryParam("redirect_uri", emailProperties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", GMAIL_SEND_SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state != null ? state : "paycore_oauth_state")
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    /**
     * Exchanges an authorization code for refresh and access tokens.
     */
    public TokenExchangeResult exchangeAuthorizationCode(String code) {
        if (!emailProperties.isConfigured() && (emailProperties.getClientId() == null || emailProperties.getClientSecret() == null)) {
            throw new EmailDeliveryUnavailableException("Google OAuth Client ID and Secret must be configured before code exchange.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", emailProperties.getClientId());
        body.add("client_secret", emailProperties.getClientSecret());
        body.add("code", code);
        body.add("grant_type", "authorization_code");
        body.add("redirect_uri", emailProperties.getRedirectUri());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(GOOGLE_TOKEN_URL, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                String accessToken = json.path("access_token").asText(null);
                String refreshToken = json.path("refresh_token").asText(null);
                int expiresIn = json.path("expires_in").asInt(3600);

                if (accessToken == null) {
                    throw new EmailDeliveryFailedException("Google OAuth token response did not contain an access_token.");
                }

                // Cache the newly received access token
                cachedToken.set(new CachedAccessToken(accessToken, Instant.now().plusSeconds(Math.max(60, expiresIn - 60))));

                log.info("Successfully exchanged Google OAuth authorization code for tokens (refresh_token present: {})", refreshToken != null);
                return new TokenExchangeResult(accessToken, refreshToken, expiresIn);
            } else {
                throw new EmailDeliveryFailedException("Unexpected response from Google OAuth server: " + response.getStatusCode());
            }
        } catch (HttpStatusCodeException ex) {
            log.error("Google OAuth token exchange failed with status code: {}", ex.getStatusCode());
            throw new EmailDeliveryFailedException("Google OAuth token exchange failed. Please verify credentials.", ex);
        } catch (Exception ex) {
            log.error("Error communicating with Google OAuth endpoint: {}", ex.getMessage());
            throw new EmailDeliveryFailedException("Failed to exchange Google OAuth code: " + ex.getMessage(), ex);
        }
    }

    /**
     * Obtains a valid access token, using an unexpired cached token if available,
     * or refreshing using the configured refresh_token.
     */
    public String getValidAccessToken() {
        CachedAccessToken current = cachedToken.get();
        if (current != null && current.isValid()) {
            return current.getAccessToken();
        }

        if (emailProperties.getClientId() == null || emailProperties.getClientId().isBlank()
                || emailProperties.getClientSecret() == null || emailProperties.getClientSecret().isBlank()
                || emailProperties.getRefreshToken() == null || emailProperties.getRefreshToken().isBlank()) {
            throw new EmailDeliveryUnavailableException("Google OAuth credentials (client-id, client-secret, refresh-token) are not fully configured.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", emailProperties.getClientId());
        body.add("client_secret", emailProperties.getClientSecret());
        body.add("refresh_token", emailProperties.getRefreshToken());
        body.add("grant_type", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(GOOGLE_TOKEN_URL, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                String accessToken = json.path("access_token").asText(null);
                int expiresIn = json.path("expires_in").asInt(3600);

                if (accessToken == null) {
                    throw new EmailDeliveryFailedException("Google OAuth refresh response did not contain an access_token.");
                }

                cachedToken.set(new CachedAccessToken(accessToken, Instant.now().plusSeconds(Math.max(60, expiresIn - 60))));
                log.debug("Successfully refreshed Google OAuth access token (expires in {}s)", expiresIn);
                return accessToken;
            } else {
                throw new EmailDeliveryFailedException("Failed to refresh Google OAuth access token: HTTP " + response.getStatusCode());
            }
        } catch (HttpStatusCodeException ex) {
            log.error("Google OAuth token refresh rejected with HTTP status: {}", ex.getStatusCode());
            throw new EmailDeliveryFailedException("Google OAuth token refresh failed. Verify that the refresh token has not been revoked.", ex);
        } catch (Exception ex) {
            log.error("Failed to connect to Google OAuth token endpoint: {}", ex.getMessage());
            throw new EmailDeliveryFailedException("Error refreshing Google OAuth access token: " + ex.getMessage(), ex);
        }
    }
}
