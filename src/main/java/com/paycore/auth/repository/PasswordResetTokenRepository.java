package com.paycore.auth.repository;

import com.paycore.auth.entity.PasswordResetToken;
import com.paycore.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByUserAndConsumedAtIsNull(User user);

    @Modifying
    void deleteByExpiresAtBefore(Instant expiry);
}
