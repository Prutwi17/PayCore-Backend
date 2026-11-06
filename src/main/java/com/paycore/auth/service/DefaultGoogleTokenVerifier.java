package com.paycore.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.paycore.auth.dto.GoogleIdTokenPayload;
import com.paycore.common.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;

/**
 * Validates Google OpenID Connect ID Tokens using Google's official client verification library.
 * Enforces cryptographic RSA-256 signature verification, issuer check, audience check, and expiration check.
 */
@Service
public class DefaultGoogleTokenVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultGoogleTokenVerifier.class);

    private final String configuredClientId;
    private final GoogleIdTokenVerifier verifier;

    public DefaultGoogleTokenVerifier(
            @Value("${paycore.google.client-id:${GOOGLE_CLIENT_ID:${paycore.email.client-id:}}}") String clientId
    ) {
        this.configuredClientId = clientId != null ? clientId.trim() : "";

        GoogleIdTokenVerifier.Builder builder = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        ).setIssuers(Arrays.asList("accounts.google.com", "https://accounts.google.com"));

        if (!this.configuredClientId.isBlank()) {
            builder.setAudience(Collections.singletonList(this.configuredClientId));
        }

        this.verifier = builder.build();
    }

    @Override
    public GoogleIdTokenPayload verify(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new UnauthorizedException("Google ID token is required");
        }

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString.trim());
            if (idToken == null) {
                log.warn("Google ID token verification failed: null token returned by GoogleIdTokenVerifier");
                throw new UnauthorizedException("Invalid, expired, or untrusted Google ID token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            String sub = payload.getSubject();
            if (sub == null || sub.isBlank()) {
                throw new UnauthorizedException("Invalid Google token: missing subject (sub) claim");
            }

            String email = payload.getEmail();
            if (email == null || email.isBlank()) {
                throw new UnauthorizedException("Invalid Google token: missing email claim");
            }

            Boolean emailVerified = payload.getEmailVerified();
            if (!Boolean.TRUE.equals(emailVerified)) {
                log.warn("Rejected Google sign-in for account with unverified email: [{}]", email);
                throw new UnauthorizedException("Google account email is not verified. Please verify your Google email address first.");
            }

            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            return new GoogleIdTokenPayload(sub, email, true, name, picture);
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Cryptographic verification of Google ID token failed: {}", ex.getMessage());
            throw new UnauthorizedException("Google authentication failed: " + ex.getMessage());
        }
    }
}
