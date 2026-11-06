package com.paycore.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Google OpenID Connect ID token credential submitted for authentication or registration")
public class GoogleAuthRequest {

    @NotBlank(message = "Google ID token is required")
    @Schema(description = "Raw Google JWT ID token received from Google Identity Services callback", example = "eyJhbGciOiJSUzI1NiIs...")
    private String idToken;

    public GoogleAuthRequest() {
    }

    public GoogleAuthRequest(String idToken) {
        this.idToken = idToken;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
