package com.paycore.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.event.model.PayCoreDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Manages persistence and lifecycle of outbox events.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists an outbox event in the same transactional context as the calling business logic.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent recordEvent(PayCoreDomainEvent domainEvent) {
        try {
            String payloadJson = objectMapper.writeValueAsString(domainEvent);
            OutboxEvent outboxEvent = new OutboxEvent(
                    domainEvent.getEventId(),
                    domainEvent.getAggregateType(),
                    domainEvent.getAggregateId(),
                    domainEvent.getEventType().name(),
                    domainEvent.getMerchantId(),
                    payloadJson
            );
            OutboxEvent saved = outboxEventRepository.save(outboxEvent);
            log.debug("Recorded outbox event [id={}, type={}, aggregate={}] in transaction",
                    domainEvent.getEventId(), domainEvent.getEventType(), domainEvent.getAggregateId());
            return saved;
        } catch (Exception e) {
            log.error("Failed to serialize and record outbox event: {}", e.getMessage(), e);
            throw new RuntimeException("Could not persist outbox event", e);
        }
    }

    @Transactional
    public void markPublished(String eventId) {
        outboxEventRepository.findByEventId(eventId).ifPresent(event -> {
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            outboxEventRepository.save(event);
            log.debug("Marked outbox event [{}] as PUBLISHED", eventId);
        });
    }

    @Transactional
    public void markFailed(String eventId, String errorMessage, int retryCount, Instant nextRetryAt) {
        outboxEventRepository.findByEventId(eventId).ifPresent(event -> {
            event.setStatus(OutboxStatus.FAILED);
            event.setRetryCount(retryCount);
            event.setNextRetryAt(nextRetryAt);
            event.setLastError(errorMessage);
            outboxEventRepository.save(event);
            log.warn("Marked outbox event [{}] as FAILED (attempt={}), next retry at: {}", eventId, retryCount, nextRetryAt);
        });
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> findUnpublishedEvents(Pageable pageable) {
        return outboxEventRepository.findUnpublishedEventsReadyForDispatch(Instant.now(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<OutboxEvent> listOutboxEvents(OutboxStatus status, Pageable pageable) {
        if (status != null) {
            return outboxEventRepository.findByStatus(status, pageable);
        }
        return outboxEventRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<OutboxEvent> getByEventId(String eventId) {
        return outboxEventRepository.findByEventId(eventId);
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return outboxEventRepository.countPendingEvents();
    }

    @Transactional(readOnly = true)
    public long countFailed() {
        return outboxEventRepository.countFailedEvents();
    }
}
