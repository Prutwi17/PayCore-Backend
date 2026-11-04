package com.paycore.merchant.repository;

import com.paycore.merchant.entity.Merchant;
import com.paycore.merchant.entity.MerchantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByMerchantCode(String merchantCode);

    Optional<Merchant> findByBusinessEmail(String businessEmail);

    Optional<Merchant> findByUserId(Long userId);

    Page<Merchant> findByStatus(MerchantStatus status, Pageable pageable);

    boolean existsByBusinessEmail(String businessEmail);

    boolean existsByMerchantCode(String merchantCode);
}
