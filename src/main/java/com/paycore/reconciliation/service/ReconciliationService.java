package com.paycore.reconciliation.service;

import com.paycore.audit.service.AuditLogService;
import com.paycore.common.exception.DuplicateResourceException;
import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.payment.entity.Payment;
import com.paycore.payment.repository.PaymentRepository;
import com.paycore.reconciliation.adapter.ProviderReconciliationAdapter;
import com.paycore.reconciliation.adapter.ProviderRefundRecord;
import com.paycore.reconciliation.adapter.ProviderTransactionRecord;
import com.paycore.reconciliation.dto.DiscrepancyResolutionRequest;
import com.paycore.reconciliation.dto.ReconciliationRecordResponseDto;
import com.paycore.reconciliation.dto.ReconciliationRunRequest;
import com.paycore.reconciliation.dto.ReconciliationRunResponseDto;
import com.paycore.reconciliation.entity.*;
import com.paycore.reconciliation.repository.ReconciliationRecordRepository;
import com.paycore.reconciliation.repository.ReconciliationRunRepository;
import com.paycore.refund.entity.Refund;
import com.paycore.refund.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationRecordRepository recordRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ProviderReconciliationAdapter providerAdapter;
    private final ReconciliationMatcher reconciliationMatcher;
    private final AuditLogService auditLogService;

    public ReconciliationService(
            ReconciliationRunRepository runRepository,
            ReconciliationRecordRepository recordRepository,
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            ProviderReconciliationAdapter providerAdapter,
            ReconciliationMatcher reconciliationMatcher,
            AuditLogService auditLogService
    ) {
        this.runRepository = runRepository;
        this.recordRepository = recordRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.providerAdapter = providerAdapter;
        this.reconciliationMatcher = reconciliationMatcher;
        this.auditLogService = auditLogService;
    }

    /**
     * Triggers a new reconciliation run with concurrency control and safe batch execution.
     */
    public ReconciliationRunResponseDto initiateReconciliationRun(ReconciliationRunRequest request, String initiatedBy) {
        // Concurrency guard: reject new run if another run is currently executing
        if (runRepository.existsByStatus(RunStatus.RUNNING)) {
            throw new DuplicateResourceException("A reconciliation run is already actively running. Please wait for it to complete.");
        }

        String runReference = "rec_run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        ReconciliationRun run = new ReconciliationRun(
                runReference,
                request.getRunType() != null ? request.getRunType() : RunType.FULL,
                initiatedBy,
                request.getStartDate(),
                request.getEndDate()
        );
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run = runRepository.save(run);

        auditLogService.logAction(
                initiatedBy,
                "ROLE_ADMIN",
                null,
                "RECONCILIATION_RUN_INITIATED",
                "RECONCILIATION",
                runReference,
                "Initiated " + run.getRunType() + " reconciliation run",
                null,
                Map.of("runReference", runReference, "runType", run.getRunType().name()),
                "Manual admin reconciliation trigger"
        );

        // Execute matching
        return executeReconciliation(run);
    }

    /**
     * Executes the reconciliation process adhering to the Zero-DB Network I/O Principle.
     */
    private ReconciliationRunResponseDto executeReconciliation(ReconciliationRun run) {
        Instant start = run.getScopeStartDate();
        Instant end = run.getScopeEndDate();

        try {
            // STEP 1: Fetch external provider statement records (Network I/O OUTSIDE DB transaction)
            List<ProviderTransactionRecord> providerTxRecords = Collections.emptyList();
            List<ProviderRefundRecord> providerRfRecords = Collections.emptyList();

            if (run.getRunType() == RunType.PAYMENTS || run.getRunType() == RunType.FULL) {
                providerTxRecords = providerAdapter.fetchTransactionRecords(start, end, null);
            }
            if (run.getRunType() == RunType.REFUNDS || run.getRunType() == RunType.FULL) {
                providerRfRecords = providerAdapter.fetchRefundRecords(start, end, null);
            }

            // STEP 2: Fetch internal database records
            List<Payment> internalPayments = Collections.emptyList();
            List<Refund> internalRefunds = Collections.emptyList();

            if (run.getRunType() == RunType.PAYMENTS || run.getRunType() == RunType.FULL) {
                internalPayments = paymentRepository.findByDateRange(start, end);
            }
            if (run.getRunType() == RunType.REFUNDS || run.getRunType() == RunType.FULL) {
                internalRefunds = refundRepository.findByDateRange(start, end);
            }

            // STEP 3: Match via deterministic matcher
            List<ReconciliationRecord> matchedRecords = new ArrayList<>();

            if (run.getRunType() == RunType.PAYMENTS || run.getRunType() == RunType.FULL) {
                matchedRecords.addAll(reconciliationMatcher.matchPayments(run, internalPayments, providerTxRecords));
            }
            if (run.getRunType() == RunType.REFUNDS || run.getRunType() == RunType.FULL) {
                matchedRecords.addAll(reconciliationMatcher.matchRefunds(run, internalRefunds, providerRfRecords));
            }

            // STEP 4: Persist reconciliation records in safe batches
            saveRecordsInBatches(matchedRecords, 100);

            // STEP 5: Compute summary metrics
            int total = matchedRecords.size();
            int matched = (int) matchedRecords.stream().filter(r -> r.getDiscrepancyType() == DiscrepancyType.MATCHED).count();
            int discrepancies = total - matched;

            run.setTotalRecords(total);
            run.setMatchedRecords(matched);
            run.setDiscrepancyRecords(discrepancies);
            run.setFailedRecords(0);
            run.setCompletedAt(Instant.now());
            run.setStatus(discrepancies == 0 ? RunStatus.COMPLETED : RunStatus.COMPLETED_WITH_ERRORS);

            ReconciliationRun savedRun = runRepository.save(run);

            auditLogService.logAction(
                    run.getInitiatedBy(),
                    "ROLE_ADMIN",
                    null,
                    "RECONCILIATION_RUN_COMPLETED",
                    "RECONCILIATION",
                    run.getRunReference(),
                    String.format("Completed %s run with %d matched, %d discrepancies out of %d records",
                            run.getRunType(), matched, discrepancies, total),
                    null,
                    Map.of(
                            "runReference", run.getRunReference(),
                            "status", run.getStatus().name(),
                            "totalRecords", total,
                            "matchedRecords", matched,
                            "discrepancyRecords", discrepancies
                    ),
                    "Reconciliation execution completed"
            );

            log.info("Reconciliation run [{}] completed. Total: {}, Matched: {}, Discrepancies: {}",
                    run.getRunReference(), total, matched, discrepancies);

            return ReconciliationRunResponseDto.fromEntity(savedRun);

        } catch (Exception ex) {
            log.error("Reconciliation run [{}] failed with error: {}", run.getRunReference(), ex.getMessage(), ex);
            run.setStatus(RunStatus.FAILED);
            run.setErrorSummary(ex.getMessage() != null ? ex.getMessage() : "Unexpected reconciliation engine failure");
            run.setCompletedAt(Instant.now());
            ReconciliationRun failedRun = runRepository.save(run);

            auditLogService.logAction(
                    run.getInitiatedBy(),
                    "ROLE_ADMIN",
                    null,
                    "RECONCILIATION_RUN_FAILED",
                    "RECONCILIATION",
                    run.getRunReference(),
                    "Reconciliation run failed: " + ex.getMessage(),
                    null,
                    Map.of("runReference", run.getRunReference(), "error", ex.getMessage()),
                    "Reconciliation engine failure"
            );

            return ReconciliationRunResponseDto.fromEntity(failedRun);
        }
    }

    private void saveRecordsInBatches(List<ReconciliationRecord> records, int batchSize) {
        for (int i = 0; i < records.size(); i += batchSize) {
            int toIndex = Math.min(i + batchSize, records.size());
            recordRepository.saveAll(records.subList(i, toIndex));
        }
    }

    /**
     * Resolves an identified discrepancy with administrator notes, reason, and an immutable audit log.
     * Does NOT silently overwrite original financial or payment records.
     */
    @Transactional
    public ReconciliationRecordResponseDto resolveDiscrepancy(
            Long recordId,
            DiscrepancyResolutionRequest request,
            String adminUsername
    ) {
        ReconciliationRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation record not found with id: " + recordId));

        if (record.getStatus() == RecordStatus.RESOLVED) {
            throw new IllegalStateException("Discrepancy record is already resolved by " + record.getResolvedBy());
        }

        if (record.getDiscrepancyType() == DiscrepancyType.MATCHED) {
            throw new IllegalArgumentException("Cannot resolve a record that is already clean and matched");
        }

        Map<String, Object> beforeData = Map.of(
                "id", record.getId(),
                "status", record.getStatus().name(),
                "discrepancyType", record.getDiscrepancyType().name(),
                "resolutionNotes", record.getResolutionNotes() != null ? record.getResolutionNotes() : "",
                "resolvedBy", record.getResolvedBy() != null ? record.getResolvedBy() : ""
        );

        record.setStatus(RecordStatus.RESOLVED);
        record.setResolutionNotes(request.getNotes().trim());
        record.setResolvedBy(adminUsername);
        record.setResolvedAt(Instant.now());

        ReconciliationRecord savedRecord = recordRepository.save(record);

        Map<String, Object> afterData = Map.of(
                "id", savedRecord.getId(),
                "status", savedRecord.getStatus().name(),
                "discrepancyType", savedRecord.getDiscrepancyType().name(),
                "resolutionNotes", savedRecord.getResolutionNotes(),
                "resolvedBy", savedRecord.getResolvedBy(),
                "resolvedAt", savedRecord.getResolvedAt().toString(),
                "reason", request.getReason()
        );

        auditLogService.logAction(
                adminUsername,
                "ROLE_ADMIN",
                null,
                "DISCREPANCY_RESOLVED",
                "RECONCILIATION_RECORD",
                String.valueOf(record.getId()),
                "Manual discrepancy resolution: " + request.getReason(),
                beforeData,
                afterData,
                request.getNotes()
        );

        log.info("Discrepancy [{}] marked RESOLVED by admin [{}]", recordId, adminUsername);

        return ReconciliationRecordResponseDto.fromEntity(savedRecord);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationRunResponseDto> getRuns(RunStatus status, Pageable pageable) {
        return runRepository.findFiltered(status, pageable).map(ReconciliationRunResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public ReconciliationRunResponseDto getRunByReference(String runReference) {
        ReconciliationRun run = runRepository.findByRunReference(runReference)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation run not found: " + runReference));
        return ReconciliationRunResponseDto.fromEntity(run);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationRecordResponseDto> getRecordsByRun(
            String runReference,
            RecordStatus status,
            DiscrepancyType discrepancyType,
            RecordType recordType,
            Pageable pageable
    ) {
        ReconciliationRun run = runRepository.findByRunReference(runReference)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation run not found: " + runReference));
        return recordRepository.findByRunAndFilters(run.getId(), status, discrepancyType, recordType, pageable)
                .map(ReconciliationRecordResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationRecordResponseDto> getDiscrepancies(
            RecordStatus status,
            DiscrepancyType discrepancyType,
            RecordType recordType,
            Pageable pageable
    ) {
        return recordRepository.findDiscrepancies(status, discrepancyType, recordType, pageable)
                .map(ReconciliationRecordResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public ReconciliationRecordResponseDto getDiscrepancyById(Long id) {
        ReconciliationRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation record not found with id: " + id));
        return ReconciliationRecordResponseDto.fromEntity(record);
    }
}
