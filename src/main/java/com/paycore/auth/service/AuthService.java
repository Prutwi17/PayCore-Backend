package com.paycore.auth.service;

import com.paycore.auth.dto.AuthResponse;
import com.paycore.auth.dto.LoginRequest;
import com.paycore.auth.dto.RegisterRequest;
import com.paycore.common.exception.DuplicateResourceException;
import com.paycore.common.exception.UnauthorizedException;
import com.paycore.common.util.HashUtil;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            MerchantRepository merchantRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public UserResponseDto register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        String username = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            username = username + "_" + System.currentTimeMillis();
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setStatus(UserStatus.ACTIVE);

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

        log.info("Successfully registered user [{}] with role [ROLE_MERCHANT]", savedUser.getEmail());
        return UserResponseDto.fromEntity(savedUser, merchantCode);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Failed authentication attempt for email: {}", email);
            throw new UnauthorizedException("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Blocked login for inactive user [{}], status: {}", email, user.getStatus());
            throw new UnauthorizedException("User account is " + user.getStatus().name().toLowerCase() + ". Please contact support.");
        }

        String merchantCode = null;
        if (user.getMerchantId() != null) {
            merchantCode = merchantRepository.findById(user.getMerchantId())
                    .map(Merchant::getMerchantCode)
                    .orElse(null);
        }

        SecurityUserPrincipal principal = SecurityUserPrincipal.create(user);
        String token = tokenProvider.generateToken(principal);

        UserResponseDto userDto = UserResponseDto.fromEntity(user, merchantCode);
        log.info("Successful login for user [{}], role count: {}", email, user.getRoles().size());
        return new AuthResponse(token, tokenProvider.getExpirationMs() / 1000, userDto);
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
