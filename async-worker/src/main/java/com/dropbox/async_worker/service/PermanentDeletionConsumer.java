package com.dropbox.async_worker.service;

import com.dropbox.async_worker.client.MetadataServiceClient;
import com.dropbox.async_worker.event.EventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * WRK-02: consumes FILE_PERMANENTLY_DELETED from file.lifecycle.v1 and
 * deletes that file's MinIO bytes. Deliberately no local database - async-worker
 * stays stateless (see plan): "duplicate delete request safe" comes from
 * MinIO's own removeObjects idempotency (deleting an already-gone object is a
 * no-op, same property UploadTempPartsCleaner already relies on), and
 * "cleanup status observable" comes from structured logging plus the DLT
 * topic itself (see KafkaConsumerConfig) being the durable signal of a
 * permanently-failed cleanup. A MinIO failure is left to propagate so the
 * container's retry/DLT handling takes over - no local exception swallowing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermanentDeletionConsumer {

    private static final String EVENT_TYPE_FILE_PERMANENTLY_DELETED = "FILE_PERMANENTLY_DELETED";

    private final MetadataServiceClient metadataServiceClient;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${minio.bucket}")
    private String bucket;

    @KafkaListener(topics = "file.lifecycle.v1", groupId = "async-worker")
    public void onMessage(String message) throws Exception {
        EventEnvelope<JsonNode> envelope;
        try {
            envelope = parse(message);
        } catch (Exception e) {
            log.error("Discarding unparseable file.lifecycle.v1 message: {}", e.getMessage());
            return;
        }

        if (!EVENT_TYPE_FILE_PERMANENTLY_DELETED.equals(envelope.eventType())) {
            return;
        }

        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("requestId", envelope.eventId().toString());
        try {
            UUID fileId = envelope.aggregateId();
            MDC.put("fileId", fileId.toString());
            List<String> objectKeys = metadataServiceClient.getVersionObjectKeys(envelope.userId(), fileId);
            if (objectKeys.isEmpty()) {
                log.info("No MinIO objects to clean up for permanently-deleted file {}", fileId);
                return;
            }

            deleteObjects(fileId, objectKeys);
        } finally {
            MDC.remove("eventId");
            MDC.remove("requestId");
            MDC.remove("fileId");
        }
    }

    private void deleteObjects(UUID fileId, List<String> objectKeys) throws Exception {
        List<DeleteObject> toDelete = new ArrayList<>();
        for (String objectKey : objectKeys) {
            toDelete.add(new DeleteObject(objectKey));
        }

        Iterable<Result<DeleteError>> results = minioClient.removeObjects(
                RemoveObjectsArgs.builder().bucket(bucket).objects(toDelete).build());
        for (Result<DeleteError> result : results) {
            result.get(); // force evaluation to actually issue each delete and surface any error
        }

        log.info("Deleted {} MinIO object(s) for permanently-deleted file {}", objectKeys.size(), fileId);
    }

    private EventEnvelope<JsonNode> parse(String message) throws JsonProcessingException {
        JavaType type = objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, JsonNode.class);
        return objectMapper.readValue(message, type);
    }
}
