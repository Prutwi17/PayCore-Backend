package com.paycore.audit.service;

import com.paycore.audit.dto.AuditLogResponseDto;
import com.paycore.audit.entity.AuditLog;
import com.paycore.audit.repository.AuditLogRepository;
import com.paycore.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Service for managing immutable, append-only audit records.
 * Provides sanitization before persistence and ensures audit records cannot be mutated or deleted.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final AuditDataSanitizer dataSanitizer;

    public AuditLogService(AuditLogRepository auditLogRepository, AuditDataSanitizer dataSanitizer) {
        this.auditLogRepository = auditLogRepository;
        this.dataSanitizer = dataSanitizer;
    }

    /**
     * Persists an audit log entry in a new transaction or current transaction.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog logAction(String actorId, String actorRole, Long merchantId, String action,
                              String resourceType, String resourceId, String description,
                              Object beforeData, Object afterData, String reason) {
        String sanitizedBefore = dataSanitizer.sanitize(beforeData);
        String sanitizedAfter = dataSanitizer.sanitize(afterData);

        AuditLog entry = new AuditLog(
                actorId,
                actorRole,
                merchantId,
                action,
                resourceType,
                resourceId,
                description,
                sanitizedBefore,
                sanitizedAfter,
                reason,
                null,
                null
        );

        AuditLog saved = auditLogRepository.save(entry);
        log.info("Audit logged: [action={}, resource={}:{}, actor={}]", action, resourceType, resourceId, actorId);
        return saved;
    }

    /**
     * Overload that auto-detects current security context principal and roles.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog logAction(String action, String resourceType, String resourceId,
                              String description, Object beforeData, Object afterData, String reason) {
        String actorId = "SYSTEM";
        String actorRole = "SYSTEM";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            actorId = auth.getName();
            actorRole = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","));
        }

        return logAction(actorId, actorRole, null, action, resourceType, resourceId, description, beforeData, afterData, reason);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponseDto> getAuditLogs(
            String actorId,
            String action,
            String resourceType,
            String resourceId,
            Long merchantId,
            Instant startDate,
            Instant endDate,
            Pageable pageable
    ) {
        return auditLogRepository.findFiltered(
                actorId,
                action,
                resourceType,
                resourceId,
                merchantId,
                startDate,
                endDate,
                pageable
        ).map(AuditLogResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public AuditLogResponseDto getAuditLogById(Long id) {
        AuditLog logEntry = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit log entry not found with id: " + id));
        return AuditLogResponseDto.fromEntity(logEntry);
    }
}
