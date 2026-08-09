package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.OutboxEvent;
import com.dropbox.metadata_service.domain.OutboxEventStatus;
import com.dropbox.metadata_service.domain.ProcessedEvent;
import com.dropbox.metadata_service.dto.MaterializeFileResponse;
import com.dropbox.metadata_service.event.EventEnvelope;
import com.dropbox.metadata_service.event.FileEventData;
import com.dropbox.metadata_service.repository.OutboxEventRepository;
import com.dropbox.metadata_service.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * EVT-02's second (separate, deliberately not-ambient-shared) transaction
 * step: records that eventId has been fully handled, and enqueues the
 * corresponding file.lifecycle.v1 event for OBX-02 to later publish. Kept
 * apart from the materialize() call itself - see UploadCompletedEventConsumer
 * for why one big ambient transaction across both steps would risk the
 * Postgres "transaction aborted" trap that materialize()'s own conflict
 * handling depends on running clear of.
 */
@Component
@RequiredArgsConstructor
class UploadEventBookkeepingWriter {

    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordProcessed(UUID eventId, String consumerName, UUID userId,
                                 MaterializeFileResponse materialized, boolean isNewFile) {
        processedEventRepository.save(new ProcessedEvent(eventId, consumerName, Instant.now()));

        String eventType = isNewFile ? "FILE_CREATED" : "FILE_VERSION_CREATED";
        FileEventData data = new FileEventData(materialized.fileId(), materialized.versionId(), materialized.versionNumber());
        EventEnvelope<FileEventData> envelope = new EventEnvelope<>(
                UUID.randomUUID(), eventType, 1, Instant.now(), materialized.fileId(), userId, data);

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("FILE")
                .aggregateId(materialized.fileId())
                .eventType(eventType)
                .payload(serialize(envelope))
                .status(OutboxEventStatus.PENDING)
                .build();
        outboxEventRepository.save(event);
    }

    private String serialize(EventEnvelope<FileEventData> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
