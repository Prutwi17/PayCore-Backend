package com.paycore.event.outbox;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventId(String eventId);

    /**
     * Finds pending or failed events ready for (re)publishing.
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' OR (e.status = 'FAILED' AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)) ORDER BY e.createdAt ASC")
    List<OutboxEvent> findUnpublishedEventsReadyForDispatch(@Param("now") Instant now, Pageable pageable);

    Page<OutboxEvent> findByStatus(OutboxStatus status, Pageable pageable);

    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.status = 'PENDING'")
    long countPendingEvents();

    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.status = 'FAILED'")
    long countFailedEvents();
}
