package com.paycore.reconciliation.repository;

import com.paycore.reconciliation.entity.ReconciliationRun;
import com.paycore.reconciliation.entity.RunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {

    Optional<ReconciliationRun> findByRunReference(String runReference);

    boolean existsByStatus(RunStatus status);

    Optional<ReconciliationRun> findTop1ByOrderByCreatedAtDesc();

    @Query("""
        SELECT r FROM ReconciliationRun r
        WHERE (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt DESC
    """)
    Page<ReconciliationRun> findFiltered(@Param("status") RunStatus status, Pageable pageable);
}
