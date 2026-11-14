package com.paycore.auth.service;

import com.paycore.audit.service.AuditLogService;
import com.paycore.auth.dto.AuthResponse;
import com.paycore.auth.dto.GoogleAuthRequest;
import com.paycore.auth.dto.GoogleIdTokenPayload;
import com.paycore.auth.entity.RefreshToken;
import com.paycore.auth.repository.AdminSetupTokenRepository;
import com.paycore.auth.repository.EmailVerificationTokenRepository;
import com.paycore.auth.repository.PasswordResetTokenRepository;
import com.paycore.auth.repository.RefreshTokenRepository;
import com.paycore.common.exception.UnauthorizedException;
import com.paycore.email.EmailService;
import com.paycore.merchant.entity.Merchant;
import com.paycore.merchant.repository.MerchantRepository;
import com.paycore.security.jwt.JwtTokenProvider;
import com.paycore.security.service.SecurityUserPrincipal;
import com.paycore.user.entity.Role;
import com.paycore.user.entity.User;
import com.paycore.user.entity.UserExternalIdentity;
import com.paycore.user.entity.UserStatus;
import com.paycore.user.repository.RoleRepository;
import com.paycore.user.repository.UserExternalIdentityRepository;
import com.paycore.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

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

    @Mock
    private UserExternalIdentityRepository userExternalIdentityRepository;

    @Mock
    private GoogleTokenVerifier googleTokenVerifier;

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
                auditLogService,
                userExternalIdentityRepository,
                googleTokenVerifier
        );
    }

    @Test
    @DisplayName("Should register new merchant user and link external Google identity")
    void loginWithGoogle_whenNewUser_registersAndLinks() {
        String idToken = "valid.google.id.token";
        GoogleIdTokenPayload payload = new GoogleIdTokenPayload(
                "google_sub_1001", "merchant@paycore.dev", true, "Test Merchant", null
        );

        when(googleTokenVerifier.verify(idToken)).thenReturn(payload);
        when(userExternalIdentityRepository.findByProviderAndProviderSubject("GOOGLE", "google_sub_1001"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("merchant@paycore.dev")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_random_pwd");

        Role merchantRole = new Role(Role.ROLE_MERCHANT);
        merchantRole.setId(2L);
        when(roleRepository.findByName(Role.ROLE_MERCHANT)).thenReturn(Optional.of(merchantRole));

        User savedUser = new User();
        savedUser.setId(10L);
        savedUser.setEmail("merchant@paycore.dev");
        savedUser.setFullName("Test Merchant");
        savedUser.setStatus(UserStatus.ACTIVE);
        savedUser.addRole(merchantRole);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        Merchant savedMerchant = new Merchant();
        savedMerchant.setId(5L);
        savedMerchant.setMerchantCode("mer_test");
        when(merchantRepository.save(any(Merchant.class))).thenReturn(savedMerchant);

        when(tokenProvider.generateToken(any(SecurityUserPrincipal.class))).thenReturn("access_token_jwt");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.loginWithGoogle(new GoogleAuthRequest(idToken));

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access_token_jwt");
        assertThat(response.getUser().getEmail()).isEqualTo("merchant@paycore.dev");

        verify(userExternalIdentityRepository).save(any(UserExternalIdentity.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should login directly when Google external identity is already mapped")
    void loginWithGoogle_whenAlreadyLinked_logsInDirectly() {
        String idToken = "existing.google.token";
        GoogleIdTokenPayload payload = new GoogleIdTokenPayload(
                "google_sub_2002", "alice@example.com", true, "Alice", null
        );

        User existingUser = new User();
        existingUser.setId(15L);
        existingUser.setEmail("alice@example.com");
        existingUser.setFullName("Alice");
        existingUser.setStatus(UserStatus.ACTIVE);
        existingUser.addRole(new Role(Role.ROLE_MERCHANT));

        UserExternalIdentity identity = new UserExternalIdentity(
                "GOOGLE", "google_sub_2002", existingUser, "alice@example.com"
        );

        when(googleTokenVerifier.verify(idToken)).thenReturn(payload);
        when(userExternalIdentityRepository.findByProviderAndProviderSubject("GOOGLE", "google_sub_2002"))
                .thenReturn(Optional.of(identity));
        when(userRepository.save(existingUser)).thenReturn(existingUser);
        when(tokenProvider.generateToken(any(SecurityUserPrincipal.class))).thenReturn("jwt_existing_user");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.loginWithGoogle(new GoogleAuthRequest(idToken));

        assertThat(response.getAccessToken()).isEqualTo("jwt_existing_user");
        assertThat(response.getUser().getEmail()).isEqualTo("alice@example.com");
        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("Should block login when user account is suspended")
    void loginWithGoogle_whenUserSuspended_throwsUnauthorized() {
        String idToken = "suspended.user.token";
        GoogleIdTokenPayload payload = new GoogleIdTokenPayload(
                "google_sub_suspended", "bad@example.com", true, "Bad User", null
        );

        User suspendedUser = new User();
        suspendedUser.setId(99L);
        suspendedUser.setEmail("bad@example.com");
        suspendedUser.setStatus(UserStatus.SUSPENDED);

        UserExternalIdentity identity = new UserExternalIdentity(
                "GOOGLE", "google_sub_suspended", suspendedUser, "bad@example.com"
        );

        when(googleTokenVerifier.verify(idToken)).thenReturn(payload);
        when(userExternalIdentityRepository.findByProviderAndProviderSubject("GOOGLE", "google_sub_suspended"))
                .thenReturn(Optional.of(identity));

        assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleAuthRequest(idToken)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    @DisplayName("Should reject blank or missing Google ID token")
    void loginWithGoogle_whenEmptyToken_throwsUnauthorized() {
        assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleAuthRequest("   ")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("required");
    }
}
