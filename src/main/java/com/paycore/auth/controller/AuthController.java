package com.paycore.auth.controller;

import com.paycore.auth.dto.AuthResponse;
import com.paycore.auth.dto.LoginRequest;
import com.paycore.auth.dto.RegisterRequest;
import com.paycore.auth.service.AuthService;
import com.paycore.common.response.ApiResponse;
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

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication & Onboarding", description = "Endpoints for user registration, login, and session validation")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register user / merchant", description = "Creates a new user and optional merchant account with ROLE_MERCHANT.")
    public ResponseEntity<ApiResponse<UserResponseDto>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponseDto user = authService.register(request);
        return new ResponseEntity<>(ApiResponse.created("User registered successfully", user), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Validates credentials and issues a signed JWT access token.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", response));
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
