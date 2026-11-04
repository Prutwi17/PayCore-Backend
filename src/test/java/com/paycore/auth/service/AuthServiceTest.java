package com.paycore.auth.service;

import com.paycore.auth.dto.AuthResponse;
import com.paycore.auth.dto.LoginRequest;
import com.paycore.auth.dto.RegisterRequest;
import com.paycore.common.exception.DuplicateResourceException;
import com.paycore.common.exception.UnauthorizedException;
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
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, merchantRepository, passwordEncoder, tokenProvider);
    }

    @Test
    @DisplayName("Registration should succeed and hash password with BCrypt")
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
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        Merchant savedMerchant = new Merchant("mer_12345678", "Jane Store", "jane@example.com", "+1234567890", "123 Market St", 10L);
        savedMerchant.setId(20L);
        when(merchantRepository.save(any(Merchant.class))).thenReturn(savedMerchant);

        UserResponseDto result = authService.register(request);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("jane@example.com");
        assertThat(result.getFullName()).isEqualTo("Jane Doe");
        verify(passwordEncoder).encode("Password123!");
        verify(userRepository, atLeastOnce()).save(any(User.class));
    }

    @Test
    @DisplayName("Registration should reject duplicate email addresses with DuplicateResourceException")
    void testRegisterDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("Jane Doe", "existing@example.com", "Password123!", null, null, null);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("User already exists with email: 'existing@example.com'");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Login should authenticate valid credentials and return JWT token")
    void testLoginValid() {
        LoginRequest request = new LoginRequest("user@example.com", "Password123!");

        User user = new User("user@example.com", "user@example.com", "$2a$12$hash", "Test User", null);
        user.setId(5L);
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(new Role(Role.ROLE_MERCHANT));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "$2a$12$hash")).thenReturn(true);
        when(tokenProvider.generateToken(any(SecurityUserPrincipal.class))).thenReturn("mock.jwt.token");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUser().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("Login should reject invalid password with UnauthorizedException")
    void testLoginInvalidPassword() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword!");

        User user = new User("user@example.com", "user@example.com", "$2a$12$hash", "Test User", null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword!", "$2a$12$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email or password");

        verify(tokenProvider, never()).generateToken(any());
    }
}
