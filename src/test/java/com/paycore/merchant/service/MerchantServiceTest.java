package com.paycore.merchant.service;

import com.paycore.common.exception.ForbiddenException;
import com.paycore.common.exception.ResourceNotFoundException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private UserRepository userRepository;

    private MerchantService merchantService;

    @BeforeEach
    void setUp() {
        merchantService = new MerchantService(merchantRepository, userRepository);
    }

    @Test
    @DisplayName("Create merchant should persist merchant and associate with user")
    void testCreateMerchant() {
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "Acme Global", "acme@business.com", "+123456", "Main Street"
        );

        User user = new User("acme@business.com", "acme@business.com", "hash", "Acme Admin", null);
        user.setId(10L);

        when(merchantRepository.existsByBusinessEmail("acme@business.com")).thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        Merchant saved = new Merchant("mer_abc123", "Acme Global", "acme@business.com", "+123456", "Main Street", 10L);
        saved.setId(50L);
        when(merchantRepository.save(any(Merchant.class))).thenReturn(saved);

        MerchantResponseDto result = merchantService.createMerchant(request, 10L);

        assertThat(result).isNotNull();
        assertThat(result.getBusinessName()).isEqualTo("Acme Global");
        assertThat(result.getStatus()).isEqualTo(MerchantStatus.PENDING);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Merchant should be able to access own record, but forbidden from accessing another merchant's record")
    void testMerchantOwnershipCheck() {
        // Merchant User with merchantId = 100
        SecurityUserPrincipal merchantPrincipal = new SecurityUserPrincipal(
                1L, "m1@test.com", "m1@test.com", "pass", "M1", 100L, true,
                List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"))
        );

        // Attempting to access merchantId = 200 (different merchant!)
        assertThatThrownBy(() -> merchantService.getMerchantById(200L, merchantPrincipal))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You are not authorized to access this merchant record");

        // Now Admin attempts to access merchantId = 200
        SecurityUserPrincipal adminPrincipal = new SecurityUserPrincipal(
                99L, "admin@test.com", "admin@test.com", "pass", "Admin", null, true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        Merchant merchant200 = new Merchant("mer_200", "Store 200", "s200@test.com", null, null, 2L);
        merchant200.setId(200L);
        when(merchantRepository.findById(200L)).thenReturn(Optional.of(merchant200));

        MerchantResponseDto adminResult = merchantService.getMerchantById(200L, adminPrincipal);
        assertThat(adminResult).isNotNull();
        assertThat(adminResult.getId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("Admin status updates (approve, suspend, reject) should update merchant state")
    void testUpdateMerchantStatus() {
        Merchant merchant = new Merchant("mer_001", "Pending Store", "store@test.com", null, null, 1L);
        merchant.setId(5L);
        merchant.setStatus(MerchantStatus.PENDING);

        when(merchantRepository.findById(5L)).thenReturn(Optional.of(merchant));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Approve
        MerchantResponseDto approved = merchantService.updateMerchantStatus(5L, MerchantStatus.ACTIVE);
        assertThat(approved.getStatus()).isEqualTo(MerchantStatus.ACTIVE);

        // Suspend
        MerchantResponseDto suspended = merchantService.updateMerchantStatus(5L, MerchantStatus.SUSPENDED);
        assertThat(suspended.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);
    }
}
