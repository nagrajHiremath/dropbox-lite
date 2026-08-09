package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.OutboxEvent;
import com.dropbox.metadata_service.domain.OutboxEventStatus;
import com.dropbox.metadata_service.event.EventEnvelope;
import com.dropbox.metadata_service.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * OBX-02: shared helper for enqueueing this service's own outbox_events rows
 * (FILE_TRASHED, FILE_RESTORED, FILE_PERMANENTLY_DELETED, FILE_SHARED,
 * SHARE_REVOKED - see FileService/ShareService). Deliberately NOT
 * @Transactional itself: FileService's and ShareService's mutating methods
 * are already @Transactional at the method level (unlike upload-service's
 * deliberately-per-step-checkpointed UploadCompletionService), so a plain
 * repository.save() call here naturally joins the caller's already-active
 * transaction and commits atomically with the file/share mutation - no
 * REQUIRES_NEW-separate-bean dance needed for this call site.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public <T> void enqueue(String aggregateType, UUID aggregateId, String eventType, UUID userId, T data) {
        EventEnvelope<T> envelope = new EventEnvelope<>(
                UUID.randomUUID(), eventType, 1, Instant.now(), aggregateId, userId, data);

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(serialize(envelope))
                .status(OutboxEventStatus.PENDING)
                .build();
        outboxEventRepository.save(event);
    }

    private String serialize(EventEnvelope<?> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
