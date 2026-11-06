package com.paycore.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Immutable append-only audit log entity.
 * Records security-sensitive, financial, and administrative operations across the platform.
 * Update and delete operations are strictly prohibited at both the application and ORM level.
 */
@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_logs_created_at", columnList = "created_at DESC"),
                @Index(name = "idx_audit_logs_resource", columnList = "resource_type, resource_id"),
                @Index(name = "idx_audit_logs_actor", columnList = "actor_id"),
                @Index(name = "idx_audit_logs_merchant", columnList = "merchant_id"),
                @Index(name = "idx_audit_logs_action", columnList = "action")
        }
)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", length = 128)
    private String actorId;

    @Column(name = "actor_role", length = 64)
    private String actorRole;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "resource_id", length = 128)
    private String resourceId;

    @Column(name = "description", length = 1000)
    private String description;

    @Lob
    @Column(name = "before_data", columnDefinition = "LONGTEXT")
    private String beforeData;

    @Lob
    @Column(name = "after_data", columnDefinition = "LONGTEXT")
    private String afterData;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AuditLog() {
    }

    public AuditLog(String actorId, String actorRole, Long merchantId, String action,
                    String resourceType, String resourceId, String description,
                    String beforeData, String afterData, String reason,
                    String requestId, String ipAddress) {
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.merchantId = merchantId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.description = description;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.reason = reason;
        this.requestId = requestId;
        this.ipAddress = ipAddress;
        this.createdAt = Instant.now();
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
