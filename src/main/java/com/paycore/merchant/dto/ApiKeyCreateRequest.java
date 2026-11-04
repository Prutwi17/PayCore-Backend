package com.paycore.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ApiKeyCreateRequest {

    @NotBlank(message = "Key type is required")
    @Pattern(regexp = "SECRET|PUBLISHABLE", message = "Key type must be SECRET or PUBLISHABLE")
    private String keyType = "SECRET";

    @NotBlank(message = "Environment is required")
    @Pattern(regexp = "TEST|LIVE", message = "Environment must be TEST or LIVE")
    private String environment = "TEST";

    public ApiKeyCreateRequest() {
    }

    public ApiKeyCreateRequest(String keyType, String environment) {
        this.keyType = keyType;
        this.environment = environment;
    }

    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }
}
