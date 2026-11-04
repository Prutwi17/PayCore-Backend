package com.paycore.merchant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MerchantRegistrationRequest {

    @NotBlank(message = "Business name is required")
    @Size(min = 2, max = 200, message = "Business name must be between 2 and 200 characters")
    private String businessName;

    @NotBlank(message = "Business email is required")
    @Email(message = "Business email must be valid")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    private String businessEmail;

    private String contactNumber;
    private String businessAddress;

    public MerchantRegistrationRequest() {
    }

    public MerchantRegistrationRequest(String businessName, String businessEmail, String contactNumber, String businessAddress) {
        this.businessName = businessName;
        this.businessEmail = businessEmail;
        this.contactNumber = contactNumber;
        this.businessAddress = businessAddress;
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
}
