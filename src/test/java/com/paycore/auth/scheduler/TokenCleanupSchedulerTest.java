package com.paycore.auth.scheduler;

import com.paycore.auth.repository.AdminSetupTokenRepository;
import com.paycore.auth.repository.EmailVerificationTokenRepository;
import com.paycore.auth.repository.PasswordResetTokenRepository;
import com.paycore.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AdminSetupTokenRepository adminSetupTokenRepository;

    private TokenCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TokenCleanupScheduler(
                emailVerificationTokenRepository,
                passwordResetTokenRepository,
                refreshTokenRepository,
                adminSetupTokenRepository
        );
    }

    @Test
    @DisplayName("Should invoke deleteByExpiresAtBefore on all token repositories")
    void testCleanupExpiredTokensSuccess() {
        scheduler.cleanupExpiredTokens();

        verify(emailVerificationTokenRepository, times(1)).deleteByExpiresAtBefore(any(Instant.class));
        verify(passwordResetTokenRepository, times(1)).deleteByExpiresAtBefore(any(Instant.class));
        verify(refreshTokenRepository, times(1)).deleteByExpiresAtBefore(any(Instant.class));
        verify(adminSetupTokenRepository, times(1)).deleteByExpiresAtBefore(any(Instant.class));
    }

    @Test
    @DisplayName("Should handle repository exception gracefully without crashing scheduler")
    void testCleanupHandlesExceptionGracefully() {
        doThrow(new RuntimeException("Database error"))
                .when(emailVerificationTokenRepository).deleteByExpiresAtBefore(any(Instant.class));

        scheduler.cleanupExpiredTokens();

        verify(emailVerificationTokenRepository, times(1)).deleteByExpiresAtBefore(any(Instant.class));
    }
}
