package com.paycore.merchant.dto;

import com.paycore.merchant.entity.Merchant;
import com.paycore.merchant.entity.MerchantStatus;

import java.time.Instant;

public class MerchantResponseDto {

    private Long id;
    private String merchantCode;
    private String businessName;
    private String businessEmail;
    private String contactNumber;
    private String businessAddress;
    private MerchantStatus status;
    private Long userId;
    private Instant createdAt;
    private Instant updatedAt;

    public MerchantResponseDto() {
    }

    public static MerchantResponseDto fromEntity(Merchant merchant) {
        MerchantResponseDto dto = new MerchantResponseDto();
        dto.setId(merchant.getId());
        dto.setMerchantCode(merchant.getMerchantCode());
        dto.setBusinessName(merchant.getBusinessName());
        dto.setBusinessEmail(merchant.getBusinessEmail());
        dto.setContactNumber(merchant.getContactNumber());
        dto.setBusinessAddress(merchant.getBusinessAddress());
        dto.setStatus(merchant.getStatus());
        dto.setUserId(merchant.getUserId());
        dto.setCreatedAt(merchant.getCreatedAt());
        dto.setUpdatedAt(merchant.getUpdatedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public void setMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getBusinessEmail() {
        return businessEmail;
    }

    public void setBusinessEmail(String businessEmail) {
        this.businessEmail = businessEmail;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getBusinessAddress() {
        return businessAddress;
    }

    public void setBusinessAddress(String businessAddress) {
        this.businessAddress = businessAddress;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public void setStatus(MerchantStatus status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
