package com.paycore.audit.dto;

import com.paycore.audit.entity.AuditLog;
import java.time.Instant;

public class AuditLogResponseDto {

    private Long id;
    private String actorId;
    private String actorRole;
    private Long merchantId;
    private String action;
    private String resourceType;
    private String resourceId;
    private String description;
    private String beforeData;
    private String afterData;
    private String reason;
    private String requestId;
    private String ipAddress;
    private Instant createdAt;

    public AuditLogResponseDto() {
    }

    public static AuditLogResponseDto fromEntity(AuditLog entity) {
        AuditLogResponseDto dto = new AuditLogResponseDto();
        dto.setId(entity.getId());
        dto.setActorId(entity.getActorId());
        dto.setActorRole(entity.getActorRole());
        dto.setMerchantId(entity.getMerchantId());
        dto.setAction(entity.getAction());
        dto.setResourceType(entity.getResourceType());
        dto.setResourceId(entity.getResourceId());
        dto.setDescription(entity.getDescription());
        dto.setBeforeData(entity.getBeforeData());
        dto.setAfterData(entity.getAfterData());
        dto.setReason(entity.getReason());
        dto.setRequestId(entity.getRequestId());
        dto.setIpAddress(entity.getIpAddress());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBeforeData() {
        return beforeData;
    }

    public void setBeforeData(String beforeData) {
        this.beforeData = beforeData;
    }

    public String getAfterData() {
        return afterData;
    }

    public void setAfterData(String afterData) {
        this.afterData = afterData;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
