package com.paycore.auth.controller;

import com.paycore.auth.dto.*;
import com.paycore.auth.service.AuthService;
import com.paycore.common.response.ApiResponse;
import com.paycore.email.EmailService;
import com.paycore.email.GmailOAuthService;
import com.paycore.email.dto.EmailStatusDto;
import com.paycore.security.service.SecurityUserPrincipal;
import com.paycore.user.dto.UserResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication & Onboarding", description = "Endpoints for user registration, verification, login, password recovery, and admin setup")
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;
    private final GmailOAuthService gmailOAuthService;

    public AuthController(
            AuthService authService,
            EmailService emailService,
            GmailOAuthService gmailOAuthService
    ) {
        this.authService = authService;
        this.emailService = emailService;
        this.gmailOAuthService = gmailOAuthService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register user / merchant", description = "Creates a new user and optional merchant account in unverified email status and dispatches a real verification email via Gmail API.")
    public ResponseEntity<ApiResponse<UserResponseDto>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponseDto user = authService.register(request);
        return new ResponseEntity<>(
                ApiResponse.created("Registration successful. Please verify your email address via the link sent to your inbox.", user),
                HttpStatus.CREATED
        );
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email address", description = "Validates the one-time verification token and marks user email as verified.")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestBody(required = false) VerifyEmailRequest body
    ) {
        String token = (body != null && body.getToken() != null) ? body.getToken() : queryToken;
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success("Email address successfully verified. You may now sign in.", null));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email", description = "Generates and sends a new verification link to an unverified email address. Generic response prevents account enumeration.")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(
                "If an unverified account exists with that email address, a new verification link has been sent.",
                null
        ));
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Validates credentials, verifies active and verified email status, and returns access and persistent refresh tokens.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Rotates refresh token and issues a new signed JWT access token.")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Revokes server-side refresh token and invalidates active session.")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password request", description = "Sends a 15-minute one-time password reset link to the registered email address. Generic response prevents enumeration.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "If an account exists with this email address, a password reset link has been sent.",
                null
        ));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Validates the one-time password reset token, updates password with BCrypt, invalidates all active sessions, and dispatches confirmation email.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been successfully reset. Please log in with your new password.", null));
    }

    @PostMapping("/admin/initiate-setup")
    @Operation(summary = "Initiate initial administrator onboarding", description = "Dispatches secure one-time onboarding invitation email to omp27614@gmail.com. Fails if admin is already configured.")
    public ResponseEntity<ApiResponse<Void>> initiateAdminSetup(@RequestBody(required = false) AdminInitiateSetupRequest request) {
        authService.initiateAdminSetup(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Initial administrator onboarding invitation successfully dispatched to omp27614@gmail.com.",
                null
        ));
    }

    @PostMapping("/admin/complete-setup")
    @Operation(summary = "Complete initial administrator onboarding", description = "Sets master administrator credentials and verifies email ownership using the secure setup token.")
    public ResponseEntity<ApiResponse<Void>> completeAdminSetup(@Valid @RequestBody AdminSetupRequest request) {
        authService.completeAdminSetup(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Platform administrator account has been successfully configured and activated. You may now sign in.",
                null
        ));
    }

    @GetMapping("/email-status")
    @Operation(summary = "Check email service status", description = "Reports whether Google OAuth email integration is fully configured and lists any missing properties without exposing secrets.")
    public ResponseEntity<ApiResponse<EmailStatusDto>> getEmailStatus() {
        EmailStatusDto status = emailService.getStatus();
        return ResponseEntity.ok(ApiResponse.success("Email service configuration status", status));
    }

    @GetMapping("/google/oauth-url")
    @Operation(summary = "Get Google OAuth authorization URL", description = "Generates Google OAuth 2.0 Consent Screen URL to authorize Gmail sender account.")
    public ResponseEntity<ApiResponse<Map<String, String>>> getGoogleOAuthUrl(@RequestParam(value = "state", required = false) String state) {
        String authUrl = gmailOAuthService.buildAuthorizationUrl(state);
        return ResponseEntity.ok(ApiResponse.success("Google OAuth authorization URL", Map.of("authorizationUrl", authUrl)));
    }

    @GetMapping("/google/callback")
    @Operation(summary = "Google OAuth callback handler", description = "Receives authorization code from Google OAuth flow, exchanges it for refresh token, and returns configuration guidance.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleGoogleCallback(@RequestParam("code") String code) {
        GmailOAuthService.TokenExchangeResult result = gmailOAuthService.exchangeAuthorizationCode(code);
        return ResponseEntity.ok(ApiResponse.success(
                "Google OAuth authorization exchange successful! Save the refresh token in your environment as GOOGLE_REFRESH_TOKEN.",
                Map.of(
                        "refreshToken", result.getRefreshToken() != null ? result.getRefreshToken() : "EXCHANGE_COMPLETED_NO_NEW_REFRESH_TOKEN_RETURNED",
                        "expiresIn", result.getExpiresIn(),
                        "instruction", "Set GOOGLE_REFRESH_TOKEN environment variable in your backend environment to enable automated email sending."
                )
        ));
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get current authenticated user",
            description = "Retrieves user and tenant profile from active JWT session.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<UserResponseDto>> getCurrentUser(
            @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        UserResponseDto user = authService.getCurrentUser(principal);
        return ResponseEntity.ok(ApiResponse.success("Current user profile retrieved", user));
    }
}
