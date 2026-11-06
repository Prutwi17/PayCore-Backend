package com.paycore.event.inbox;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InboxEventRepository extends JpaRepository<InboxEvent, Long> {

    Optional<InboxEvent> findByEventIdAndConsumerName(String eventId, String consumerName);

    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);

    Page<InboxEvent> findByConsumerName(String consumerName, Pageable pageable);

    Page<InboxEvent> findByProcessingStatus(InboxStatus processingStatus, Pageable pageable);
}
