package com.paycore.merchant.repository;

import com.paycore.merchant.entity.MerchantApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantApiKeyRepository extends JpaRepository<MerchantApiKey, Long> {

    List<MerchantApiKey> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    Optional<MerchantApiKey> findByKeyHash(String keyHash);

    Optional<MerchantApiKey> findByIdAndMerchantId(Long id, Long merchantId);
}
