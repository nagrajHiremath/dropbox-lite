package com.dropbox.upload_service.service;

import com.dropbox.upload_service.client.MetadataServiceClient;
import com.dropbox.upload_service.domain.IdempotencyKey;
import com.dropbox.upload_service.domain.UploadSession;
import com.dropbox.upload_service.domain.UploadStatus;
import com.dropbox.upload_service.domain.UploadType;
import com.dropbox.upload_service.dto.InitiateUploadRequest;
import com.dropbox.upload_service.dto.InitiateUploadResponse;
import com.dropbox.upload_service.exception.DependencyUnavailableException;
import com.dropbox.upload_service.exception.IdempotencyConflictException;
import com.dropbox.upload_service.repository.UploadSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadInitiationService {

    private static final String OPERATION = "INITIATE_UPLOAD";
    private static final long DEFAULT_CHUNK_SIZE = 8L * 1024 * 1024; // 8 MiB, matches the design doc's example
    private static final int MAX_PARTS = 10_000; // MinIO composeObject source-object limit
    private static final int MAX_POLL_ATTEMPTS = 10;
    private static final long POLL_INTERVAL_MILLIS = 50;

    private final UploadSessionRepository uploadSessionRepository;
    private final IdempotencyKeyWriter idempotencyKeyWriter;
    private final MetadataServiceClient metadataServiceClient;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${upload.session.expiration-hours:24}")
    private long sessionExpirationHours;

    public InitiateUploadResponse initiate(UUID ownerId, InitiateUploadRequest request, String idempotencyKey) {
        if (idempotencyKey == null) {
            return createSession(ownerId, request);
        }

        String requestHash = hashRequest(ownerId, request);

        if (!tryReserve(ownerId, idempotencyKey, requestHash)) {
            return awaitReservationOwner(ownerId, idempotencyKey, requestHash);
        }

        InitiateUploadResponse response;
        try {
            response = createSession(ownerId, request);
        } catch (RuntimeException e) {
            idempotencyKeyWriter.release(ownerId, OPERATION, idempotencyKey);
            throw e;
        }

        idempotencyKeyWriter.finalizeSuccess(ownerId, OPERATION, idempotencyKey, response.uploadId(), 201, serialize(response));
        return response;
    }

    /**
     * The actual side-effecting work: validate, check storage, persist the session.
     * Only ever reached by whichever request holds the idempotency reservation (or
     * by requests that didn't send an Idempotency-Key at all), so at most one
     * UploadSession is ever created per key.
     */
    private InitiateUploadResponse createSession(UUID ownerId, InitiateUploadRequest request) {
        if (request.folderId() != null) {
            metadataServiceClient.requireOwnedFolder(request.folderId(), ownerId);
        }

        ensureStorageBackendReachable();

        long chunkSize = selectChunkSize(request.size());
        int totalParts = calculateTotalParts(request.size(), chunkSize);
        // Decoupled from the entity's own generated id: we don't rely on being able to
        // predict or pre-assign the Hibernate-generated UUID primary key.
        String objectKey = "dropbox-files/%s/%s/data".formatted(ownerId, UUID.randomUUID());

        UploadSession session = UploadSession.builder()
                .userId(ownerId)
                .folderId(request.folderId())
                .uploadType(UploadType.NEW_FILE)
                .fileName(request.fileName())
                .mimeType(request.mimeType())
                .totalSize(request.size())
                .chunkSize(chunkSize)
                .totalParts(totalParts)
                .objectKey(objectKey)
                .status(UploadStatus.INITIATED)
                .expiresAt(Instant.now().plus(Duration.ofHours(sessionExpirationHours)))
                .build();
        session = uploadSessionRepository.save(session);

        return new InitiateUploadResponse(
                session.getId(), session.getChunkSize(), session.getTotalParts(), session.getStatus().name());
    }

    private boolean tryReserve(UUID ownerId, String idempotencyKey, String requestHash) {
        IdempotencyKey record = IdempotencyKey.builder()
                .userId(ownerId)
                .idempotencyKey(idempotencyKey)
                .operation(OPERATION)
                .requestHash(requestHash)
                .status("IN_PROGRESS")
                .expiresAt(Instant.now().plus(Duration.ofHours(sessionExpirationHours)))
                .build();
        try {
            idempotencyKeyWriter.reserve(record);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Expected: another request already holds this Idempotency-Key (unique
            // constraint on user_id/operation/idempotency_key is the race-detection
            // mechanism itself, not a fault). Deliberately no exception/stack trace
            // here - awaitReservationOwner() looks up and handles the winner next.
            log.debug("Idempotency-Key reservation lost the race (expected): userId={}, operation={}, idempotencyKey={}",
                    ownerId, OPERATION, idempotencyKey);
            return false;
        }
    }

    /**
     * Lost the reservation race. Poll briefly for the winner to finish (initiation
     * work is a couple of fast calls, not a long-running operation) and replay its
     * result. A hash mismatch means the same key was reused for a different request.
     */
    private InitiateUploadResponse awaitReservationOwner(UUID ownerId, String idempotencyKey, String requestHash) {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            Optional<IdempotencyKey> found = idempotencyKeyWriter.find(ownerId, OPERATION, idempotencyKey);
            if (found.isEmpty()) {
                // The reservation holder's work failed and released it; stop waiting so
                // the caller can retry cleanly rather than waiting out the full timeout.
                break;
            }

            IdempotencyKey record = found.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException("Idempotency-Key was already used with a different request");
            }
            if (record.getResponseBody() != null) {
                return deserialize(record.getResponseBody());
            }

            sleepQuietly(POLL_INTERVAL_MILLIS);
        }
        throw new IdempotencyConflictException(
                "A concurrent request with the same Idempotency-Key did not complete in time; please retry");
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdempotencyConflictException("Interrupted while waiting for a concurrent identical request");
        }
    }

    private void ensureStorageBackendReachable() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                throw new DependencyUnavailableException("Storage bucket '" + bucket + "' does not exist");
            }
        } catch (DependencyUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyUnavailableException("Storage backend is unavailable", e);
        }
    }

    static long selectChunkSize(long totalSize) {
        long minChunkSizeForPartLimit = (totalSize + MAX_PARTS - 1) / MAX_PARTS;
        return Math.max(DEFAULT_CHUNK_SIZE, minChunkSizeForPartLimit);
    }

    static int calculateTotalParts(long totalSize, long chunkSize) {
        return (int) ((totalSize + chunkSize - 1) / chunkSize);
    }

    private String hashRequest(UUID ownerId, InitiateUploadRequest request) {
        String canonical = String.join("|",
                ownerId.toString(),
                request.fileName(),
                String.valueOf(request.folderId()),
                String.valueOf(request.size()),
                String.valueOf(request.mimeType()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String serialize(InitiateUploadResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
    }

    private InitiateUploadResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, InitiateUploadResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize stored idempotent response", e);
        }
    }
}
