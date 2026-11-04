package com.paycore.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MerchantUpdateRequest {

    @NotBlank(message = "Business name cannot be blank")
    @Size(min = 2, max = 200, message = "Business name must be between 2 and 200 characters")
    private String businessName;

    private String contactNumber;
    private String businessAddress;

    public MerchantUpdateRequest() {
    }

    public MerchantUpdateRequest(String businessName, String contactNumber, String businessAddress) {
        this.businessName = businessName;
        this.contactNumber = contactNumber;
        this.businessAddress = businessAddress;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
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
}
