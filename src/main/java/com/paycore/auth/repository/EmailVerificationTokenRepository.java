package com.paycore.auth.repository;

import com.paycore.auth.entity.EmailVerificationToken;
import com.paycore.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    List<EmailVerificationToken> findByUserAndConsumedAtIsNull(User user);

    @Modifying
    void deleteByExpiresAtBefore(Instant expiry);
}
