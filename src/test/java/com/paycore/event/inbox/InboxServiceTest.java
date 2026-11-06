package com.paycore.event.inbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Inbox Service Idempotency Unit Tests")
class InboxServiceTest {

    @Mock
    private InboxEventRepository inboxEventRepository;

    private InboxService inboxService;

    @BeforeEach
    void setUp() {
        inboxService = new InboxService(inboxEventRepository);
    }

    @Test
    @DisplayName("First time event is received returns true")
    void testFirstTimeEventReceived() {
        when(inboxEventRepository.findByEventIdAndConsumerName("evt_100", "PaymentConsumer"))
                .thenReturn(Optional.empty());

        boolean registered = inboxService.registerReceived("evt_100", "PaymentConsumer", "PAYMENT_SUCCESS");
        assertTrue(registered);
        verify(inboxEventRepository).saveAndFlush(any(InboxEvent.class));
    }

    @Test
    @DisplayName("Duplicate event already marked PROCESSED returns false")
    void testDuplicateProcessedEventSkipped() {
        InboxEvent processed = new InboxEvent("evt_100", "PaymentConsumer", "PAYMENT_SUCCESS");
        processed.setProcessingStatus(InboxStatus.PROCESSED);

        when(inboxEventRepository.findByEventIdAndConsumerName("evt_100", "PaymentConsumer"))
                .thenReturn(Optional.of(processed));

        boolean registered = inboxService.registerReceived("evt_100", "PaymentConsumer", "PAYMENT_SUCCESS");
        assertFalse(registered, "Duplicate PROCESSED event must be skipped");
        verify(inboxEventRepository, never()).saveAndFlush(any(InboxEvent.class));
    }

    @Test
    @DisplayName("Concurrent insert collision (DataIntegrityViolationException) safely returns false")
    void testConcurrentRaceConditionHandledSafely() {
        when(inboxEventRepository.findByEventIdAndConsumerName("evt_100", "PaymentConsumer"))
                .thenReturn(Optional.empty());
        when(inboxEventRepository.saveAndFlush(any(InboxEvent.class)))
                .thenThrow(new DataIntegrityViolationException("uk_inbox_event_consumer"));

        boolean registered = inboxService.registerReceived("evt_100", "PaymentConsumer", "PAYMENT_SUCCESS");
        assertFalse(registered, "Concurrent duplicate insert must return false without propagating exception");
    }
}
