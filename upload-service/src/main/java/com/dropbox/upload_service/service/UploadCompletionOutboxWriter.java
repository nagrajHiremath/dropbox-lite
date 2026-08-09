package com.dropbox.upload_service.service;

import com.dropbox.upload_service.domain.OutboxEvent;
import com.dropbox.upload_service.domain.OutboxEventStatus;
import com.dropbox.upload_service.domain.UploadSession;
import com.dropbox.upload_service.domain.UploadStatus;
import com.dropbox.upload_service.event.EventEnvelope;
import com.dropbox.upload_service.event.UploadCompletedEventData;
import com.dropbox.upload_service.repository.OutboxEventRepository;
import com.dropbox.upload_service.repository.UploadSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * OBX-01: durably records the STORAGE_COMPLETED transition and the
 * UPLOAD_COMPLETED outbox event in one atomic transaction (Transactional
 * Outbox pattern - see TECHNICAL_DESIGN.md 39). Kept as a separate bean for
 * the same reason as FileVersionMaterializer/IdempotencyKeyWriter:
 * @Transactional only takes effect through the Spring proxy, so
 * UploadCompletionService cannot call this as a private/self-invoked method.
 */
@Component
@RequiredArgsConstructor
class UploadCompletionOutboxWriter {

    private final UploadSessionRepository uploadSessionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public UploadSession markStorageCompletedAndEnqueue(UploadSession session) {
        session.setStatus(UploadStatus.STORAGE_COMPLETED);
        session = uploadSessionRepository.save(session);

        UploadCompletedEventData data = new UploadCompletedEventData(
                session.getUploadType().name(),
                session.getFileId(),
                session.getFolderId(),
                session.getFileName(),
                session.getMimeType(),
                session.getObjectKey(),
                session.getTotalSize());

        EventEnvelope<UploadCompletedEventData> envelope = new EventEnvelope<>(
                UUID.randomUUID(), "UPLOAD_COMPLETED", 1, Instant.now(),
                session.getId(), session.getUserId(), data);

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("UPLOAD")
                .aggregateId(session.getId())
                .eventType("UPLOAD_COMPLETED")
                .payload(serialize(envelope))
                .status(OutboxEventStatus.PENDING)
                .build();
        outboxEventRepository.save(event);

        return session;
    }

    private String serialize(EventEnvelope<UploadCompletedEventData> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
