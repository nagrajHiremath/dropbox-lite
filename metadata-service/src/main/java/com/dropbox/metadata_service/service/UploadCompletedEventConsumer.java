package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.dto.MaterializeFileRequest;
import com.dropbox.metadata_service.dto.MaterializeFileResponse;
import com.dropbox.metadata_service.dto.MaterializeVersionRequest;
import com.dropbox.metadata_service.event.EventEnvelope;
import com.dropbox.metadata_service.event.UploadCompletedEventData;
import com.dropbox.metadata_service.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * EVT-02: consumes UPLOAD_COMPLETED from storage.lifecycle.v1 and
 * materializes the file/version via the exact same idempotent
 * FileMaterializationService methods the synchronous HTTP path (UPL-05/VER-02)
 * already uses - no duplicated business logic. This is a safety-net path
 * (see UploadCompletionService's OBX-01 javadoc): normally the synchronous
 * call already materialized everything and this is a harmless no-op replay
 * via sourceUploadId idempotency.
 *
 * Deliberately two separate transactions rather than one big one: this
 * method calls materialize()/materializeVersion() first (each already
 * correctly self-contained/transactional), THEN records processed_events +
 * this service's own outbox row in a second step (UploadEventBookkeepingWriter).
 * Wrapping both in one ambient transaction would let a losing-race
 * DataIntegrityViolationException inside materialize() poison the whole
 * transaction - including the reload-the-winner read materialize() itself
 * depends on to recover - the same Postgres "current transaction is aborted"
 * trap UploadCompletionService/UploadInitiationService are also deliberately
 * structured to avoid. Splitting stays safe under redelivery: a crash between
 * the two steps just makes materialize() replay (idempotent) on redelivery,
 * then the second step succeeds.
 *
 * Relies on Spring Kafka's default consumer error handling (bounded retry,
 * then log-and-skip) - custom backoff/DLT routing is EVT-03, not implemented
 * here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadCompletedEventConsumer {

    private static final String CONSUMER_NAME = "metadata-service";
    private static final String EVENT_TYPE_UPLOAD_COMPLETED = "UPLOAD_COMPLETED";
    private static final String UPLOAD_TYPE_NEW_VERSION = "NEW_VERSION";

    private final ProcessedEventRepository processedEventRepository;
    private final FileMaterializationService fileMaterializationService;
    private final UploadEventBookkeepingWriter bookkeepingWriter;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "storage.lifecycle.v1", groupId = "metadata-service")
    public void onMessage(String message) {
        EventEnvelope<UploadCompletedEventData> envelope;
        try {
            envelope = parse(message);
        } catch (Exception e) {
            log.error("Discarding unparseable storage.lifecycle.v1 message: {}", e.getMessage());
            return;
        }

        if (!EVENT_TYPE_UPLOAD_COMPLETED.equals(envelope.eventType())) {
            // Forward-compatible: this topic will carry UPLOAD_ABORTED/UPLOAD_EXPIRED/OBJECT_*
            // events later - this consumer only handles UPLOAD_COMPLETED for now.
            return;
        }

        if (processedEventRepository.existsByEventIdAndConsumerName(envelope.eventId(), CONSUMER_NAME)) {
            log.debug("Skipping already-processed event {}", envelope.eventId());
            return;
        }

        UploadCompletedEventData data = envelope.data();
        boolean isNewFile = !UPLOAD_TYPE_NEW_VERSION.equals(data.uploadType());

        MaterializeFileResponse materialized = isNewFile
                ? fileMaterializationService.materialize(envelope.userId(), toFileRequest(envelope, data))
                : fileMaterializationService.materializeVersion(envelope.userId(), toVersionRequest(envelope, data));

        bookkeepingWriter.recordProcessed(envelope.eventId(), CONSUMER_NAME, envelope.userId(), materialized, isNewFile);
    }

    private EventEnvelope<UploadCompletedEventData> parse(String message) throws JsonProcessingException {
        JavaType type = objectMapper.getTypeFactory()
                .constructParametricType(EventEnvelope.class, UploadCompletedEventData.class);
        return objectMapper.readValue(message, type);
    }

    private MaterializeFileRequest toFileRequest(EventEnvelope<UploadCompletedEventData> envelope, UploadCompletedEventData data) {
        return new MaterializeFileRequest(envelope.aggregateId(), data.folderId(), data.fileName(), data.mimeType(),
                data.objectKey(), data.sizeBytes(), null, null);
    }

    private MaterializeVersionRequest toVersionRequest(EventEnvelope<UploadCompletedEventData> envelope, UploadCompletedEventData data) {
        return new MaterializeVersionRequest(envelope.aggregateId(), data.fileId(), data.objectKey(), data.sizeBytes(), null, null);
    }
}
