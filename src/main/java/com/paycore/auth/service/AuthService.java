package com.paycore.auth.service;

import com.paycore.audit.service.AuditLogService;
import com.paycore.auth.dto.*;
import com.paycore.auth.entity.AdminSetupToken;
import com.paycore.auth.entity.EmailVerificationToken;
import com.paycore.auth.entity.PasswordResetToken;
import com.paycore.auth.entity.RefreshToken;
import com.paycore.auth.exception.PasswordMismatchException;
import com.paycore.auth.exception.TokenInvalidOrExpiredException;
import com.paycore.auth.exception.UnverifiedEmailException;
import com.paycore.auth.repository.AdminSetupTokenRepository;
import com.paycore.auth.repository.EmailVerificationTokenRepository;
import com.paycore.auth.repository.PasswordResetTokenRepository;
import com.paycore.auth.repository.RefreshTokenRepository;
import com.paycore.common.exception.BaseException;
import com.paycore.common.exception.DuplicateResourceException;
import com.paycore.common.exception.UnauthorizedException;
import com.paycore.common.util.HashUtil;
import com.paycore.email.EmailService;
import com.paycore.email.exception.EmailDeliveryUnavailableException;
import com.paycore.merchant.entity.Merchant;
import com.paycore.merchant.entity.MerchantStatus;
import com.paycore.merchant.repository.MerchantRepository;
import com.paycore.security.jwt.JwtTokenProvider;
import com.paycore.security.service.SecurityUserPrincipal;
import com.paycore.user.dto.UserResponseDto;
import com.paycore.user.entity.Role;
import com.paycore.user.entity.User;
import com.paycore.user.entity.UserStatus;
import com.paycore.user.repository.RoleRepository;
import com.paycore.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INITIAL_ADMIN_EMAIL = "omp27614@gmail.com";
    private static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(7);
    private static final Duration VERIFICATION_TOKEN_VALIDITY = Duration.ofHours(24);
    private static final Duration RESET_TOKEN_VALIDITY = Duration.ofMinutes(15);
    private static final Duration ADMIN_SETUP_TOKEN_VALIDITY = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MerchantRepository merchantRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminSetupTokenRepository adminSetupTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Value("${paycore.auth.auto-verify-email:false}")
    private boolean autoVerifyEmailInTest;

    // Rate limiting map for email actions (email -> last request timestamp)
    private final Map<String, Instant> rateLimitMap = new ConcurrentHashMap<>();

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            MerchantRepository merchantRepository,
            EmailVerificationTokenRepository verificationTokenRepository,
            PasswordResetTokenRepository resetTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            AdminSetupTokenRepository adminSetupTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            EmailService emailService,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.merchantRepository = merchantRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.adminSetupTokenRepository = adminSetupTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public UserResponseDto register(RegisterRequest request) {
        if (request.getConfirmPassword() != null && !request.getPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Password and confirm password do not match");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        String username = normalizedEmail;
        if (userRepository.existsByUsername(username)) {
            username = username + "_" + System.currentTimeMillis();
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(normalizedEmail);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(autoVerifyEmailInTest);
        if (autoVerifyEmailInTest) {
            user.setEmailVerifiedAt(Instant.now());
        }

        // Security rule: Public registration NEVER assigns ROLE_ADMIN
        Role role = roleRepository.findByName(Role.ROLE_MERCHANT)
                .orElseGet(() -> roleRepository.save(new Role(Role.ROLE_MERCHANT)));
        user.addRole(role);

        User savedUser = userRepository.save(user);

        String merchantCode = null;
        if (request.getBusinessName() != null && !request.getBusinessName().isBlank()) {
            merchantCode = "mer_" + HashUtil.generateSecureToken(4);
            Merchant merchant = new Merchant();
            merchant.setMerchantCode(merchantCode);
            merchant.setBusinessName(request.getBusinessName().trim());
            merchant.setBusinessEmail(savedUser.getEmail());
            merchant.setContactNumber(request.getPhoneNumber());
            merchant.setBusinessAddress(request.getBusinessAddress());
            merchant.setStatus(MerchantStatus.PENDING);
            merchant.setUserId(savedUser.getId());
            merchant.setWebhookSecret("whsec_" + HashUtil.generateSecureToken(16));

            Merchant savedMerchant = merchantRepository.save(merchant);
            savedUser.setMerchantId(savedMerchant.getId());
            userRepository.save(savedUser);
            log.info("Registered new merchant account [{}] for user [{}]", merchantCode, savedUser.getEmail());
        }

        // Generate verification token
        String rawToken = HashUtil.generateSecureToken(32);
        String tokenHash = HashUtil.sha256Hex(rawToken);
        EmailVerificationToken verificationToken = new EmailVerificationToken(
                savedUser,
                tokenHash,
                Instant.now().plus(VERIFICATION_TOKEN_VALIDITY)
        );
        verificationTokenRepository.save(verificationToken);

        // Send real verification email via Gmail API
        try {
            emailService.sendVerificationEmail(savedUser.getEmail(), savedUser.getFullName(), rawToken);
            log.info("Dispatched email verification link to [{}]", savedUser.getEmail());
        } catch (EmailDeliveryUnavailableException ex) {
            log.warn("Email service unavailable during registration of [{}]: {}", savedUser.getEmail(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to send verification email to [{}]: {}", savedUser.getEmail(), ex.getMessage());
        }

        auditLogService.logAction(
                String.valueOf(savedUser.getId()),
                "USER",
                savedUser.getMerchantId(),
                "USER_REGISTERED",
                "User",
                String.valueOf(savedUser.getId()),
                "User registered with unverified email",
                null,
                Map.of("email", savedUser.getEmail(), "emailVerified", false),
                "User self-service registration"
        );

        log.info("Successfully registered user [{}] with unverified email state", savedUser.getEmail());
        return UserResponseDto.fromEntity(savedUser, merchantCode);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new TokenInvalidOrExpiredException("Verification token is required");
        }

        String tokenHash = HashUtil.sha256Hex(rawToken.trim());
        EmailVerificationToken token = verificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenInvalidOrExpiredException("Invalid or unrecognized email verification link"));

        if (token.isConsumed()) {
            throw new TokenInvalidOrExpiredException("This email verification link has already been used");
        }

        if (token.isExpired()) {
            throw new TokenInvalidOrExpiredException("This email verification link has expired. Please request a new verification email");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        token.setConsumedAt(Instant.now());

        userRepository.save(user);
        verificationTokenRepository.save(token);

        auditLogService.logAction(
                String.valueOf(user.getId()),
                "USER",
                user.getMerchantId(),
                "EMAIL_VERIFIED",
                "User",
                String.valueOf(user.getId()),
                "Email address successfully verified",
                Map.of("emailVerified", false),
                Map.of("emailVerified", true),
                "Email token verification"
        );

        log.info("Successfully verified email for user [{}]", user.getEmail());
    }

    @Transactional
    public void resendVerification(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();

        // Rate limiting: allow at most 1 resend per 60 seconds per email
        Instant lastRequest = rateLimitMap.get(normalizedEmail);
        if (lastRequest != null && Duration.between(lastRequest, Instant.now()).getSeconds() < 60) {
            log.warn("Rate limited resend verification request for [{}]", normalizedEmail);
            return;
        }
        rateLimitMap.put(normalizedEmail, Instant.now());

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            log.debug("Resend verification requested for non-existent email [{}]", normalizedEmail);
            return;
        }

        User user = userOpt.get();
        if (user.isEmailVerified()) {
            log.debug("Resend verification requested for already-verified email [{}]", normalizedEmail);
            return;
        }

        // Invalidate active unconsumed tokens
        List<EmailVerificationToken> activeTokens = verificationTokenRepository.findByUserAndConsumedAtIsNull(user);
        for (EmailVerificationToken t : activeTokens) {
            t.setConsumedAt(Instant.now());
        }
        verificationTokenRepository.saveAll(activeTokens);

        // Generate fresh token
        String rawToken = HashUtil.generateSecureToken(32);
        String tokenHash = HashUtil.sha256Hex(rawToken);
        EmailVerificationToken newToken = new EmailVerificationToken(
                user,
                tokenHash,
                Instant.now().plus(VERIFICATION_TOKEN_VALIDITY)
        );
        verificationTokenRepository.save(newToken);

        try {
            emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), rawToken);
            log.info("Resent verification email to [{}]", user.getEmail());
        } catch (Exception ex) {
            log.error("Failed to resend verification email to [{}]: {}", user.getEmail(), ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Failed authentication attempt for email: {}", email);
            throw new UnauthorizedException("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Blocked login for non-active user [{}], status: {}", email, user.getStatus());
            throw new UnauthorizedException("User account is " + user.getStatus().name().toLowerCase() + ". Please contact support.");
        }

        if (!user.isEmailVerified()) {
            log.warn("Blocked login for unverified user [{}]", email);
            throw new UnverifiedEmailException("Your email address has not been verified. Please check your inbox or request a new verification email.");
        }

        // Record last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String merchantCode = null;
        if (user.getMerchantId() != null) {
            merchantCode = merchantRepository.findById(user.getMerchantId())
                    .map(Merchant::getMerchantCode)
                    .orElse(null);
        }

        SecurityUserPrincipal principal = SecurityUserPrincipal.create(user);
        String accessToken = tokenProvider.generateToken(principal);

        // Generate persistent refresh token
        String rawRefreshToken = HashUtil.generateSecureToken(32);
        String refreshTokenHash = HashUtil.sha256Hex(rawRefreshToken);
        RefreshToken refreshToken = new RefreshToken(
                user,
                refreshTokenHash,
                Instant.now().plus(REFRESH_TOKEN_VALIDITY)
        );
        refreshTokenRepository.save(refreshToken);

        UserResponseDto userDto = UserResponseDto.fromEntity(user, merchantCode);

        auditLogService.logAction(
                String.valueOf(user.getId()),
                user.getRoles().isEmpty() ? "USER" : user.getRoles().iterator().next().getName(),
                user.getMerchantId(),
                "USER_LOGIN",
                "User",
                String.valueOf(user.getId()),
                "Successful user authentication",
                null,
                Map.of("email", user.getEmail(), "loginTime", Instant.now().toString()),
                "User password login"
        );

        log.info("Successful login for verified user [{}]", email);
        return new AuthResponse(accessToken, rawRefreshToken, tokenProvider.getExpirationMs() / 1000, userDto);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new UnauthorizedException("Refresh token is required");
        }

        String rawToken = request.getRefreshToken().trim();
        String tokenHash = HashUtil.sha256Hex(rawToken);

        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or unrecognized refresh token"));

        if (token.isRevoked()) {
            log.warn("Attempt to use revoked refresh token for user [{}]", token.getUser().getEmail());
            throw new UnauthorizedException("Refresh token has been revoked. Please sign in again.");
        }

        if (token.isExpired()) {
            log.warn("Attempt to use expired refresh token for user [{}]", token.getUser().getEmail());
            throw new UnauthorizedException("Refresh token has expired. Please sign in again.");
        }

        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            throw new UnauthorizedException("User account is not eligible for token refresh");
        }

        // Token rotation: Revoke old token and issue replacement
        String newRawRefreshToken = HashUtil.generateSecureToken(32);
        String newRefreshTokenHash = HashUtil.sha256Hex(newRawRefreshToken);

        token.revoke(newRefreshTokenHash);
        refreshTokenRepository.save(token);

        RefreshToken replacementToken = new RefreshToken(
                user,
                newRefreshTokenHash,
                Instant.now().plus(REFRESH_TOKEN_VALIDITY)
        );
        refreshTokenRepository.save(replacementToken);

        SecurityUserPrincipal principal = SecurityUserPrincipal.create(user);
        String newAccessToken = tokenProvider.generateToken(principal);

        String merchantCode = null;
        if (user.getMerchantId() != null) {
            merchantCode = merchantRepository.findById(user.getMerchantId())
                    .map(Merchant::getMerchantCode)
                    .orElse(null);
        }

        UserResponseDto userDto = UserResponseDto.fromEntity(user, merchantCode);
        log.info("Refreshed access and refresh token for user [{}]", user.getEmail());
        return new AuthResponse(newAccessToken, newRawRefreshToken, tokenProvider.getExpirationMs() / 1000, userDto);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            String tokenHash = HashUtil.sha256Hex(request.getRefreshToken().trim());
            Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
            if (tokenOpt.isPresent()) {
                RefreshToken token = tokenOpt.get();
                token.setRevoked(true);
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
                log.info("Revoked refresh token on logout for user [{}]", token.getUser().getEmail());
            }
        }
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return;
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Rate limit: 1 reset request per 60 seconds
        Instant lastRequest = rateLimitMap.get("pwd_" + normalizedEmail);
        if (lastRequest != null && Duration.between(lastRequest, Instant.now()).getSeconds() < 60) {
            log.warn("Rate limited forgot-password request for [{}]", normalizedEmail);
            return;
        }
        rateLimitMap.put("pwd_" + normalizedEmail, Instant.now());

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            log.debug("Forgot password requested for non-existent email [{}]", normalizedEmail);
            return;
        }

        User user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            log.warn("Forgot password requested for inactive or unverified user [{}]", normalizedEmail);
            return;
        }

        // Invalidate active reset tokens
        List<PasswordResetToken> activeTokens = resetTokenRepository.findByUserAndConsumedAtIsNull(user);
        for (PasswordResetToken t : activeTokens) {
            t.setConsumedAt(Instant.now());
        }
        resetTokenRepository.saveAll(activeTokens);

        // Generate 15-minute reset token
        String rawToken = HashUtil.generateSecureToken(32);
        String tokenHash = HashUtil.sha256Hex(rawToken);
        PasswordResetToken resetToken = new PasswordResetToken(
                user,
                tokenHash,
                Instant.now().plus(RESET_TOKEN_VALIDITY)
        );
        resetTokenRepository.save(resetToken);

        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), rawToken);
            log.info("Dispatched password reset email to [{}]", user.getEmail());
        } catch (Exception ex) {
            log.error("Failed to dispatch password reset email to [{}]: {}", user.getEmail(), ex.getMessage());
            throw ex;
        }

        auditLogService.logAction(
                String.valueOf(user.getId()),
                "USER",
                user.getMerchantId(),
                "PASSWORD_RESET_REQUESTED",
                "User",
                String.valueOf(user.getId()),
                "Password reset email dispatched",
                null,
                Map.of("email", user.getEmail()),
                "User forgot-password request"
        );
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request.getConfirmPassword() != null && !request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("New password and confirm password do not match");
        }

        if (request.getToken() == null || request.getToken().isBlank()) {
            throw new TokenInvalidOrExpiredException("Password reset token is required");
        }

        String tokenHash = HashUtil.sha256Hex(request.getToken().trim());
        PasswordResetToken token = resetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenInvalidOrExpiredException("Invalid or unrecognized password reset link"));

        if (token.isConsumed()) {
            throw new TokenInvalidOrExpiredException("This password reset link has already been used");
        }

        if (token.isExpired()) {
            throw new TokenInvalidOrExpiredException("This password reset link has expired. Please request a new one");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        token.setConsumedAt(Instant.now());

        // Revoke all existing refresh tokens for security
        List<RefreshToken> activeRefreshTokens = refreshTokenRepository.findByUserAndRevokedFalse(user);
        for (RefreshToken rt : activeRefreshTokens) {
            rt.setRevoked(true);
            rt.setRevokedAt(Instant.now());
        }
        refreshTokenRepository.saveAll(activeRefreshTokens);

        userRepository.save(user);
        resetTokenRepository.save(token);

        // Send confirmation email
        try {
            emailService.sendPasswordChangedNotification(user.getEmail(), user.getFullName());
            log.info("Sent password changed confirmation email to [{}]", user.getEmail());
        } catch (Exception ex) {
            log.warn("Failed to send password changed confirmation to [{}]: {}", user.getEmail(), ex.getMessage());
        }

        auditLogService.logAction(
                String.valueOf(user.getId()),
                "USER",
                user.getMerchantId(),
                "PASSWORD_RESET_COMPLETED",
                "User",
                String.valueOf(user.getId()),
                "User password reset successfully and active sessions invalidated",
                null,
                Map.of("email", user.getEmail()),
                "Token password reset execution"
        );

        log.info("Successfully reset password for user [{}]", user.getEmail());
    }

    @Transactional
    public void initiateAdminSetup(AdminInitiateSetupRequest request) {
        // Enforce: check if an active verified administrator already exists
        boolean activeAdminExists = userRepository.findAll().stream()
                .anyMatch(u -> u.isEmailVerified() && u.getRoles().stream().anyMatch(r -> Role.ROLE_ADMIN.equals(r.getName())));

        if (activeAdminExists) {
            throw new com.paycore.auth.exception.AdminAlreadyProvisionedException(
                    "A platform administrator has already been configured and verified."
            );
        }

        Role adminRole = roleRepository.findByName(Role.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(Role.ROLE_ADMIN)));

        User adminUser = userRepository.findByEmail(INITIAL_ADMIN_EMAIL)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(INITIAL_ADMIN_EMAIL);
                    newUser.setUsername("admin_omp27614");
                    newUser.setFullName("Platform Administrator");
                    // Unusable placeholder hash until admin sets their password
                    newUser.setPasswordHash("UNINITIALIZED_" + HashUtil.generateSecureToken(16));
                    newUser.setStatus(UserStatus.ACTIVE);
                    newUser.setEmailVerified(false);
                    newUser.addRole(adminRole);
                    return userRepository.save(newUser);
                });

        if (!adminUser.getRoles().contains(adminRole)) {
            adminUser.addRole(adminRole);
            userRepository.save(adminUser);
        }

        // Invalidate previous unconsumed setup tokens
        List<AdminSetupToken> activeTokens = adminSetupTokenRepository.findByUserAndConsumedAtIsNull(adminUser);
        for (AdminSetupToken t : activeTokens) {
            t.setConsumedAt(Instant.now());
        }
        adminSetupTokenRepository.saveAll(activeTokens);

        // Generate 24-hour setup token
        String rawToken = HashUtil.generateSecureToken(32);
        String tokenHash = HashUtil.sha256Hex(rawToken);
        AdminSetupToken setupToken = new AdminSetupToken(
                adminUser,
                tokenHash,
                Instant.now().plus(ADMIN_SETUP_TOKEN_VALIDITY)
        );
        adminSetupTokenRepository.save(setupToken);

        emailService.sendAdminSetupEmail(adminUser.getEmail(), adminUser.getFullName(), rawToken);
        log.info("Initial platform administrator setup invitation dispatched to [{}]", adminUser.getEmail());

        auditLogService.logAction(
                String.valueOf(adminUser.getId()),
                Role.ROLE_ADMIN,
                null,
                "ADMIN_SETUP_INITIATED",
                "User",
                String.valueOf(adminUser.getId()),
                "Initial administrator onboarding invitation sent",
                null,
                Map.of("email", adminUser.getEmail()),
                "System initial bootstrap"
        );
    }

    @Transactional
    public void completeAdminSetup(AdminSetupRequest request) {
        if (request.getConfirmPassword() != null && !request.getPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Password and confirm password do not match");
        }

        if (request.getToken() == null || request.getToken().isBlank()) {
            throw new TokenInvalidOrExpiredException("Admin setup token is required");
        }

        String tokenHash = HashUtil.sha256Hex(request.getToken().trim());
        AdminSetupToken token = adminSetupTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenInvalidOrExpiredException("Invalid or unrecognized administrator setup link"));

        if (token.isConsumed()) {
            throw new TokenInvalidOrExpiredException("This administrator setup link has already been used");
        }

        if (token.isExpired()) {
            throw new TokenInvalidOrExpiredException("This administrator setup link has expired. Please initiate setup again");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        user.setStatus(UserStatus.ACTIVE);

        token.setConsumedAt(Instant.now());

        userRepository.save(user);
        adminSetupTokenRepository.save(token);

        auditLogService.logAction(
                String.valueOf(user.getId()),
                Role.ROLE_ADMIN,
                null,
                "ADMIN_SETUP_COMPLETED",
                "User",
                String.valueOf(user.getId()),
                "Initial administrator credentials configured and email verified",
                null,
                Map.of("email", user.getEmail(), "emailVerified", true),
                "Initial administrator self-provisioning"
        );

        log.info("Successfully completed initial administrator setup for [{}]", user.getEmail());
    }

    @Transactional(readOnly = true)
    public UserResponseDto getCurrentUser(SecurityUserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("User session expired or user not found"));

        String merchantCode = null;
        if (user.getMerchantId() != null) {
            merchantCode = merchantRepository.findById(user.getMerchantId())
                    .map(Merchant::getMerchantCode)
                    .orElse(null);
        }

        return UserResponseDto.fromEntity(user, merchantCode);
    }
}
