package com.paycore.merchant.service;

import com.paycore.common.exception.DuplicateResourceException;
import com.paycore.common.exception.ForbiddenException;
import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.common.util.HashUtil;
import com.paycore.merchant.dto.MerchantRegistrationRequest;
import com.paycore.merchant.dto.MerchantResponseDto;
import com.paycore.merchant.dto.MerchantUpdateRequest;
import com.paycore.merchant.entity.Merchant;
import com.paycore.merchant.entity.MerchantStatus;
import com.paycore.merchant.repository.MerchantRepository;
import com.paycore.security.service.SecurityUserPrincipal;
import com.paycore.user.entity.Role;
import com.paycore.user.entity.User;
import com.paycore.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantService {

    private static final Logger log = LoggerFactory.getLogger(MerchantService.class);

    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;

    public MerchantService(MerchantRepository merchantRepository, UserRepository userRepository) {
        this.merchantRepository = merchantRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public MerchantResponseDto createMerchant(MerchantRegistrationRequest request, Long userId) {
        if (merchantRepository.existsByBusinessEmail(request.getBusinessEmail().trim().toLowerCase())) {
            throw new DuplicateResourceException("Merchant", "businessEmail", request.getBusinessEmail());
        }

        String merchantCode = "mer_" + HashUtil.generateSecureToken(4);
        Merchant merchant = new Merchant();
        merchant.setMerchantCode(merchantCode);
        merchant.setBusinessName(request.getBusinessName().trim());
        merchant.setBusinessEmail(request.getBusinessEmail().trim().toLowerCase());
        merchant.setContactNumber(request.getContactNumber());
        merchant.setBusinessAddress(request.getBusinessAddress());
        merchant.setStatus(MerchantStatus.PENDING);
        merchant.setUserId(userId);
        merchant.setWebhookSecret("whsec_" + HashUtil.generateSecureToken(16));

        Merchant savedMerchant = merchantRepository.save(merchant);

        // Update user record with merchant tenancy
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        user.setMerchantId(savedMerchant.getId());
        userRepository.save(user);

        log.info("Created merchant [{}] for user [{}]", merchantCode, user.getEmail());
        return MerchantResponseDto.fromEntity(savedMerchant);
    }

    @Transactional(readOnly = true)
    public MerchantResponseDto getMerchantProfile(Long merchantId) {
        if (merchantId == null) {
            throw new ResourceNotFoundException("User is not associated with any merchant account");
        }
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));
        return MerchantResponseDto.fromEntity(merchant);
    }

    @Transactional
    public MerchantResponseDto updateMerchantProfile(Long merchantId, MerchantUpdateRequest request) {
        if (merchantId == null) {
            throw new ResourceNotFoundException("User is not associated with any merchant account");
        }
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));

        merchant.setBusinessName(request.getBusinessName().trim());
        if (request.getContactNumber() != null) {
            merchant.setContactNumber(request.getContactNumber().trim());
        }
        if (request.getBusinessAddress() != null) {
            merchant.setBusinessAddress(request.getBusinessAddress().trim());
        }

        Merchant updated = merchantRepository.save(merchant);
        log.info("Updated profile for merchant [{}]", updated.getMerchantCode());
        return MerchantResponseDto.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public MerchantResponseDto getMerchantById(Long merchantId, SecurityUserPrincipal principal) {
        // Ownership Check: ADMIN can access any merchant; MERCHANT can only access their own
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN));

        if (!isAdmin && (principal.getMerchantId() == null || !principal.getMerchantId().equals(merchantId))) {
            log.warn("Access denied: User [{}] attempted to access unauthorized merchant [{}]", principal.getEmail(), merchantId);
            throw new ForbiddenException("You are not authorized to access this merchant record");
        }

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));
        return MerchantResponseDto.fromEntity(merchant);
    }

    @Transactional(readOnly = true)
    public Page<MerchantResponseDto> listMerchants(MerchantStatus status, Pageable pageable) {
        Page<Merchant> page = (status != null)
                ? merchantRepository.findByStatus(status, pageable)
                : merchantRepository.findAll(pageable);

        return page.map(MerchantResponseDto::fromEntity);
    }

    @Transactional
    public MerchantResponseDto updateMerchantStatus(Long merchantId, MerchantStatus newStatus) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));

        MerchantStatus oldStatus = merchant.getStatus();
        merchant.setStatus(newStatus);
        Merchant saved = merchantRepository.save(merchant);

        log.info("Merchant [{}] status updated from [{}] to [{}] by admin", saved.getMerchantCode(), oldStatus, newStatus);
        return MerchantResponseDto.fromEntity(saved);
    }
}
