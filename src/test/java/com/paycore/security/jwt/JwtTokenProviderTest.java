package com.paycore.security.jwt;

import com.paycore.security.service.SecurityUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(secret, 3600000L); // 1 hour
    }

    @Test
    @DisplayName("Generated JWT token should be valid and contain subject and claims")
    void testGenerateAndValidateToken() {
        SecurityUserPrincipal principal = new SecurityUserPrincipal(
                100L,
                "john@example.com",
                "john@example.com",
                "hashedPass",
                "John Doe",
                200L,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"))
        );

        String token = tokenProvider.generateToken(principal);

        assertThat(token).isNotNull().isNotBlank();
        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getUsernameFromToken(token)).isEqualTo("john@example.com");
        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(100L);
        assertThat(tokenProvider.getMerchantIdFromToken(token)).isEqualTo(200L);
    }

    @Test
    @DisplayName("Expired JWT token should fail validation")
    void testExpiredTokenValidation() throws InterruptedException {
        // Create token provider with 1 millisecond expiration
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(secret, 1L);

        SecurityUserPrincipal principal = new SecurityUserPrincipal(
                1L, "exp@example.com", "exp@example.com", "pass", "Exp User", null, true, List.of()
        );

        String token = shortLivedProvider.generateToken(principal);
        Thread.sleep(10); // Wait for expiration

        assertThat(shortLivedProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("Tampered JWT token should fail validation")
    void testTamperedTokenValidation() {
        SecurityUserPrincipal principal = new SecurityUserPrincipal(
                1L, "user@example.com", "user@example.com", "pass", "User", null, true, List.of()
        );

        String token = tokenProvider.generateToken(principal);
        String tamperedToken = token + "corrupt";

        assertThat(tokenProvider.validateToken(tamperedToken)).isFalse();
    }
}
