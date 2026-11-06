package com.paycore.auth.service;

import com.paycore.audit.service.AuditLogService;
import com.paycore.auth.dto.*;
import com.paycore.auth.entity.AdminSetupToken;
import com.paycore.auth.entity.EmailVerificationToken;
import com.paycore.auth.entity.PasswordResetToken;
import com.paycore.auth.entity.RefreshToken;
import com.paycore.auth.exception.AdminAlreadyProvisionedException;
import com.paycore.auth.exception.PasswordMismatchException;
import com.paycore.auth.exception.TokenInvalidOrExpiredException;
import com.paycore.auth.exception.UnverifiedEmailException;
import com.paycore.auth.repository.AdminSetupTokenRepository;
import com.paycore.auth.repository.EmailVerificationTokenRepository;
import com.paycore.auth.repository.PasswordResetTokenRepository;
import com.paycore.auth.repository.RefreshTokenRepository;
import com.paycore.common.exception.DuplicateResourceException;
import com.paycore.common.exception.UnauthorizedException;
import com.paycore.common.util.HashUtil;
import com.paycore.email.EmailService;
import com.paycore.merchant.entity.Merchant;
import com.paycore.merchant.repository.MerchantRepository;
import com.paycore.security.jwt.JwtTokenProvider;
import com.paycore.security.service.SecurityUserPrincipal;
import com.paycore.user.dto.UserResponseDto;
import com.paycore.user.entity.Role;
import com.paycore.user.entity.User;
import com.paycore.user.entity.UserStatus;
import com.paycore.user.repository.RoleRepository;
import com.paycore.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private EmailVerificationTokenRepository verificationTokenRepository;

    @Mock
    private PasswordResetTokenRepository resetTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AdminSetupTokenRepository adminSetupTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private EmailService emailService;

    @Mock
    private AuditLogService auditLogService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                roleRepository,
                merchantRepository,
                verificationTokenRepository,
                resetTokenRepository,
                refreshTokenRepository,
                adminSetupTokenRepository,
                passwordEncoder,
                tokenProvider,
                emailService,
                auditLogService
        );
    }

    @Test
    @DisplayName("Registration should succeed, save unverified user, and dispatch verification email")
    void testRegisterSuccess() {
        RegisterRequest request = new RegisterRequest(
                "Jane Doe",
                "jane@example.com",
                "Password123!",
                "+1234567890",
                "Jane Store",
                "123 Market St"
        );

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("$2a$12$hashedPasswordExample");
        when(roleRepository.findByName(Role.ROLE_MERCHANT)).thenReturn(Optional.of(new Role(Role.ROLE_MERCHANT)));

        User savedUser = new User("jane@example.com", "jane@example.com", "$2a$12$hashedPasswordExample", "Jane Doe", "+1234567890");
        savedUser.setId(10L);
        savedUser.setEmailVerified(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        Merchant savedMerchant = new Merchant("mer_12345678", "Jane Store", "jane@example.com", "+1234567890", "123 Market St", 10L);
        when(merchantRepository.save(any(Merchant.class))).thenReturn(savedMerchant);

        UserResponseDto response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.isEmailVerified()).isFalse();

        verify(verificationTokenRepository, times(1)).save(any(EmailVerificationToken.class));
        verify(emailService, times(1)).sendVerificationEmail(eq("jane@example.com"), eq("Jane Doe"), anyString());
    }

    @Test
    @DisplayName("Registration with duplicate email should throw DuplicateResourceException")
    void testRegisterDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("Jane Doe", "jane@example.com", "Password123!", null, null, null);
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Email verification should succeed with valid token")
    void testVerifyEmailSuccess() {
        String rawToken = "valid_verification_token_12345";
        String tokenHash = HashUtil.sha256Hex(rawToken);

        User user = new User("john@example.com", "john@example.com", "hash", "John Doe", null);
        user.setEmailVerified(false);

        EmailVerificationToken token = new EmailVerificationToken(user, tokenHash, Instant.now().plus(1, ChronoUnit.HOURS));
        when(verificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        authService.verifyEmail(rawToken);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(token.isConsumed()).isTrue();

        verify(userRepository, times(1)).save(user);
        verify(verificationTokenRepository, times(1)).save(token);
    }

    @Test
    @DisplayName("Email verification should fail if token is expired")
    void testVerifyEmailExpired() {
        String rawToken = "expired_token";
        String tokenHash = HashUtil.sha256Hex(rawToken);

        User user = new User("john@example.com", "john@example.com", "hash", "John", null);
        EmailVerificationToken token = new EmailVerificationToken(user, tokenHash, Instant.now().minus(1, ChronoUnit.HOURS));
        when(verificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail(rawToken))
                .isInstanceOf(TokenInvalidOrExpiredException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Email verification should fail if token already consumed (reused)")
    void testVerifyEmailReused() {
        String rawToken = "consumed_token";
        String tokenHash = HashUtil.sha256Hex(rawToken);

        User user = new User("john@example.com", "john@example.com", "hash", "John", null);
        EmailVerificationToken token = new EmailVerificationToken(user, tokenHash, Instant.now().plus(1, ChronoUnit.HOURS));
        token.setConsumedAt(Instant.now().minus(10, ChronoUnit.MINUTES));

        when(verificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail(rawToken))
                .isInstanceOf(TokenInvalidOrExpiredException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    @DisplayName("Login should be rejected if user email is not verified")
    void testLoginUnverifiedEmail() {
        LoginRequest request = new LoginRequest("unverified@example.com", "Password123!");

        User user = new User("unverified@example.com", "unverified@example.com", "$2a$12$hash", "Unverified User", null);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);

        when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "$2a$12$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnverifiedEmailException.class)
                .hasMessageContaining("not been verified");
    }

    @Test
    @DisplayName("Login should succeed for verified and active user, issuing access & refresh tokens")
    void testLoginSuccess() {
        LoginRequest request = new LoginRequest("verified@example.com", "Password123!");

        User user = new User("verified@example.com", "verified@example.com", "$2a$12$hash", "Verified User", null);
        user.setId(22L);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);

        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "$2a$12$hash")).thenReturn(true);
        when(tokenProvider.generateToken(any(SecurityUserPrincipal.class))).thenReturn("mock_jwt_access_token");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock_jwt_access_token");
        assertThat(response.getRefreshToken()).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("verified@example.com");

        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Login should fail with invalid credentials")
    void testLoginInvalidCredentials() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword");

        User user = new User("user@example.com", "user@example.com", "$2a$12$hash", "User", null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "$2a$12$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Login should be rejected for suspended user account")
    void testLoginSuspendedAccount() {
        LoginRequest request = new LoginRequest("suspended@example.com", "Password123!");

        User user = new User("suspended@example.com", "suspended@example.com", "$2a$12$hash", "Suspended User", null);
        user.setStatus(UserStatus.SUSPENDED);

        when(userRepository.findByEmail("suspended@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "$2a$12$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    @DisplayName("Forgot password should generate token and dispatch email for verified user")
    void testForgotPasswordSuccess() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("reset@example.com");

        User user = new User("reset@example.com", "reset@example.com", "hash", "Reset User", null);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);

        when(userRepository.findByEmail("reset@example.com")).thenReturn(Optional.of(user));
        when(resetTokenRepository.findByUserAndConsumedAtIsNull(user)).thenReturn(Collections.emptyList());

        authService.forgotPassword(request);

        verify(resetTokenRepository, times(1)).save(any(PasswordResetToken.class));
        verify(emailService, times(1)).sendPasswordResetEmail(eq("reset@example.com"), eq("Reset User"), anyString());
    }

    @Test
    @DisplayName("Reset password should succeed, update BCrypt password, revoke refresh tokens, and send alert")
    void testResetPasswordSuccess() {
        String rawToken = "valid_reset_token";
        String tokenHash = HashUtil.sha256Hex(rawToken);

        User user = new User("reset@example.com", "reset@example.com", "oldHash", "Reset User", null);
        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().plus(10, ChronoUnit.MINUTES));

        when(resetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewSecurePassword123!")).thenReturn("newBcryptHash");

        RefreshToken activeToken = new RefreshToken(user, "someHash", Instant.now().plus(7, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByUserAndRevokedFalse(user)).thenReturn(List.of(activeToken));

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewSecurePassword123!", "NewSecurePassword123!");
        authService.resetPassword(request);

        assertThat(user.getPasswordHash()).isEqualTo("newBcryptHash");
        assertThat(token.isConsumed()).isTrue();
        assertThat(activeToken.isRevoked()).isTrue();

        verify(userRepository, times(1)).save(user);
        verify(emailService, times(1)).sendPasswordChangedNotification(eq("reset@example.com"), eq("Reset User"));
    }

    @Test
    @DisplayName("Token refresh should rotate refresh token and return new access token")
    void testRefreshTokenSuccess() {
        String oldRawToken = "old_refresh_token_123";
        String oldTokenHash = HashUtil.sha256Hex(oldRawToken);

        User user = new User("user@example.com", "user@example.com", "hash", "Active User", null);
        user.setId(55L);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);

        RefreshToken token = new RefreshToken(user, oldTokenHash, Instant.now().plus(5, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByTokenHash(oldTokenHash)).thenReturn(Optional.of(token));
        when(tokenProvider.generateToken(any(SecurityUserPrincipal.class))).thenReturn("new_access_jwt");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        RefreshTokenRequest request = new RefreshTokenRequest(oldRawToken);
        AuthResponse response = authService.refreshToken(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new_access_jwt");
        assertThat(response.getRefreshToken()).isNotEqualTo(oldRawToken);
        assertThat(token.isRevoked()).isTrue();

        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Logout should revoke persistent refresh token")
    void testLogoutSuccess() {
        String rawToken = "logout_token_to_revoke";
        String tokenHash = HashUtil.sha256Hex(rawToken);

        User user = new User("user@example.com", "user@example.com", "hash", "User", null);
        RefreshToken token = new RefreshToken(user, tokenHash, Instant.now().plus(1, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        authService.logout(new LogoutRequest(rawToken));

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(1)).save(token);
    }

    @Test
    @DisplayName("Initial admin setup initiation should send invite to omp27614@gmail.com")
    void testInitiateAdminSetupSuccess() {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());
        when(roleRepository.findByName(Role.ROLE_ADMIN)).thenReturn(Optional.of(new Role(Role.ROLE_ADMIN)));

        User createdAdmin = new User();
        createdAdmin.setEmail("omp27614@gmail.com");
        createdAdmin.setFullName("Platform Administrator");
        when(userRepository.findByEmail("omp27614@gmail.com")).thenReturn(Optional.of(createdAdmin));

        authService.initiateAdminSetup(new AdminInitiateSetupRequest());

        verify(adminSetupTokenRepository, times(1)).save(any(AdminSetupToken.class));
        verify(emailService, times(1)).sendAdminSetupEmail(eq("omp27614@gmail.com"), eq("Platform Administrator"), anyString());
    }

    @Test
    @DisplayName("Initial admin setup initiation should fail if verified admin already exists")
    void testInitiateAdminSetupAlreadyProvisioned() {
        User existingAdmin = new User("admin@example.com", "admin@example.com", "hash", "Admin", null);
        existingAdmin.setEmailVerified(true);
        existingAdmin.addRole(new Role(Role.ROLE_ADMIN));

        when(userRepository.findAll()).thenReturn(List.of(existingAdmin));

        assertThatThrownBy(() -> authService.initiateAdminSetup(new AdminInitiateSetupRequest()))
                .isInstanceOf(AdminAlreadyProvisionedException.class);
    }

    @Test
    @DisplayName("Initial admin setup completion should set password and verify admin account")
    void testCompleteAdminSetupSuccess() {
        String rawToken = "admin_setup_token_xyz";
        String tokenHash = HashUtil.sha256Hex(rawToken);

        User adminUser = new User();
        adminUser.setEmail("omp27614@gmail.com");
        adminUser.setEmailVerified(false);

        AdminSetupToken token = new AdminSetupToken(adminUser, tokenHash, Instant.now().plus(24, ChronoUnit.HOURS));

        when(adminSetupTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("MasterAdminPass123!")).thenReturn("bcryptMasterHash");

        AdminSetupRequest request = new AdminSetupRequest(rawToken, "MasterAdminPass123!", "MasterAdminPass123!");
        authService.completeAdminSetup(request);

        assertThat(adminUser.isEmailVerified()).isTrue();
        assertThat(adminUser.getPasswordHash()).isEqualTo("bcryptMasterHash");
        assertThat(token.isConsumed()).isTrue();

        verify(userRepository, times(1)).save(adminUser);
        verify(adminSetupTokenRepository, times(1)).save(token);
    }
}
