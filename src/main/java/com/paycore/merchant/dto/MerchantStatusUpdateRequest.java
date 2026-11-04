package com.paycore.merchant.dto;

import com.paycore.merchant.entity.MerchantStatus;
import jakarta.validation.constraints.NotNull;

public class MerchantStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private MerchantStatus status;

    public MerchantStatusUpdateRequest() {
    }

    public MerchantStatusUpdateRequest(MerchantStatus status) {
        this.status = status;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public void setStatus(MerchantStatus status) {
        this.status = status;
    }
}
