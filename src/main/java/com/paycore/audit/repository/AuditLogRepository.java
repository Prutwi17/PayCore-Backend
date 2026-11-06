package com.paycore.audit.repository;

import com.paycore.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:actorId IS NULL OR a.actorId = :actorId)
          AND (:action IS NULL OR a.action = :action)
          AND (:resourceType IS NULL OR a.resourceType = :resourceType)
          AND (:resourceId IS NULL OR a.resourceId = :resourceId)
          AND (:merchantId IS NULL OR a.merchantId = :merchantId)
          AND (:startDate IS NULL OR a.createdAt >= :startDate)
          AND (:endDate IS NULL OR a.createdAt <= :endDate)
        ORDER BY a.createdAt DESC
    """)
    Page<AuditLog> findFiltered(
            @Param("actorId") String actorId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("merchantId") Long merchantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    List<AuditLog> findTop20ByOrderByCreatedAtDesc();
}
