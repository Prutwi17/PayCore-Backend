package com.paycore.auth.scheduler;

import com.paycore.auth.repository.AdminSetupTokenRepository;
import com.paycore.auth.repository.EmailVerificationTokenRepository;
import com.paycore.auth.repository.PasswordResetTokenRepository;
import com.paycore.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Periodically purges expired, revoked, and consumed tokens from the database
 * to prevent unbounded table growth and ensure optimal index performance.
 */
@Component
@Profile("!test")
public class TokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupScheduler.class);

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminSetupTokenRepository adminSetupTokenRepository;

    public TokenCleanupScheduler(
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            AdminSetupTokenRepository adminSetupTokenRepository
    ) {
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.adminSetupTokenRepository = adminSetupTokenRepository;
    }

    /**
     * Executes daily at 02:00 AM system time.
     * Purges tokens that expired more than 48 hours ago to allow sufficient margin for investigation/audit.
     */
    @Scheduled(cron = "${paycore.auth.token-cleanup.cron:0 0 2 * * ?}")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant cutoff = Instant.now().minus(48, ChronoUnit.HOURS);
        log.info("Starting scheduled token cleanup for tokens expired before: {}", cutoff);

        try {
            emailVerificationTokenRepository.deleteByExpiresAtBefore(cutoff);
            passwordResetTokenRepository.deleteByExpiresAtBefore(cutoff);
            refreshTokenRepository.deleteByExpiresAtBefore(cutoff);
            adminSetupTokenRepository.deleteByExpiresAtBefore(cutoff);
            log.info("Successfully completed scheduled token cleanup");
        } catch (Exception e) {
            log.error("Error occurred during scheduled token cleanup: {}", e.getMessage(), e);
        }
    }
}
