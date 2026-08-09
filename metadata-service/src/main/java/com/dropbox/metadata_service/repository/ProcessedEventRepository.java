package com.dropbox.metadata_service.repository;

import com.dropbox.metadata_service.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.ProcessedEventId> {

    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
