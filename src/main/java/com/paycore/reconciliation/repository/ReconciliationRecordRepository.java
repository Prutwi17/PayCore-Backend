package com.paycore.reconciliation.repository;

import com.paycore.reconciliation.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, Long> {

    @Query("""
        SELECT r FROM ReconciliationRecord r
        WHERE r.reconciliationRun.id = :runId
          AND (:status IS NULL OR r.status = :status)
          AND (:discrepancyType IS NULL OR r.discrepancyType = :discrepancyType)
          AND (:recordType IS NULL OR r.recordType = :recordType)
        ORDER BY r.id ASC
    """)
    Page<ReconciliationRecord> findByRunAndFilters(
            @Param("runId") Long runId,
            @Param("status") RecordStatus status,
            @Param("discrepancyType") DiscrepancyType discrepancyType,
            @Param("recordType") RecordType recordType,
            Pageable pageable
    );

    @Query("""
        SELECT r FROM ReconciliationRecord r
        WHERE r.discrepancyType <> 'MATCHED'
          AND (:status IS NULL OR r.status = :status)
          AND (:discrepancyType IS NULL OR r.discrepancyType = :discrepancyType)
          AND (:recordType IS NULL OR r.recordType = :recordType)
        ORDER BY r.createdAt DESC
    """)
    Page<ReconciliationRecord> findDiscrepancies(
            @Param("status") RecordStatus status,
            @Param("discrepancyType") DiscrepancyType discrepancyType,
            @Param("recordType") RecordType recordType,
            Pageable pageable
    );

    long countByStatusIn(List<RecordStatus> statuses);

    @Query("SELECT r.discrepancyType, COUNT(r) FROM ReconciliationRecord r GROUP BY r.discrepancyType")
    List<Object[]> countGroupedByDiscrepancyType();
}
