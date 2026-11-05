package com.paycore.event.inbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Manages inbox deduplication records to guarantee consumer idempotency.
 */
@Service
public class InboxService {

    private static final Logger log = LoggerFactory.getLogger(InboxService.class);

    private final InboxEventRepository inboxEventRepository;

    public InboxService(InboxEventRepository inboxEventRepository) {
        this.inboxEventRepository = inboxEventRepository;
    }

    /**
     * Atomically registers that a consumer has received an event.
     * Returns true if newly registered, false if already processed or duplicate.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerReceived(String eventId, String consumerName, String eventType) {
        Optional<InboxEvent> existing = inboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName);
        if (existing.isPresent()) {
            InboxEvent event = existing.get();
            if (event.getProcessingStatus() == InboxStatus.PROCESSED) {
                log.info("Duplicate event detected [id={}, consumer={}]. Skipping processing.", eventId, consumerName);
                return false;
            }
            return true;
        }

        try {
            InboxEvent newEvent = new InboxEvent(eventId, consumerName, eventType);
            inboxEventRepository.saveAndFlush(newEvent);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("Race condition: duplicate event registered concurrently [id={}, consumer={}].", eventId, consumerName);
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String eventId, String consumerName) {
        inboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName).ifPresent(event -> {
            event.setProcessingStatus(InboxStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            inboxEventRepository.save(event);
            log.debug("Marked inbox event [id={}, consumer={}] as PROCESSED", eventId, consumerName);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String eventId, String consumerName, String reason) {
        inboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName).ifPresent(event -> {
            event.setProcessingStatus(InboxStatus.FAILED);
            event.setFailureReason(reason);
            inboxEventRepository.save(event);
            log.warn("Marked inbox event [id={}, consumer={}] as FAILED: {}", eventId, consumerName, reason);
        });
    }

    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String eventId, String consumerName) {
        return inboxEventRepository.findByEventIdAndConsumerName(eventId, consumerName)
                .map(event -> event.getProcessingStatus() == InboxStatus.PROCESSED)
                .orElse(false);
    }
}
