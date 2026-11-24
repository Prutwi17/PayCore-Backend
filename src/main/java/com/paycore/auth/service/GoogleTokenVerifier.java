package com.paycore.auth.service;

import com.paycore.auth.dto.GoogleIdTokenPayload;

public interface GoogleTokenVerifier {

    /**
     * Verifies cryptographic signature, issuer, audience, and expiration of a Google ID token.
     *
     * @param idTokenString raw JWT ID token string
     * @return validated payload with subject (sub), verified email, and profile metadata
     */
    GoogleIdTokenPayload verify(String idTokenString);
}
