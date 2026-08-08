package com.dropbox.upload_service.service;

import com.dropbox.upload_service.client.MetadataServiceClient;
import com.dropbox.upload_service.domain.UploadSession;
import com.dropbox.upload_service.domain.UploadStatus;
import com.dropbox.upload_service.domain.UploadType;
import com.dropbox.upload_service.dto.CompleteUploadResponse;
import com.dropbox.upload_service.dto.MaterializeFileRequest;
import com.dropbox.upload_service.dto.MaterializeFileResponse;
import com.dropbox.upload_service.dto.MaterializeVersionRequest;
import com.dropbox.upload_service.exception.DependencyUnavailableException;
import com.dropbox.upload_service.exception.InvalidRequestException;
import com.dropbox.upload_service.exception.InvalidUploadStateException;
import com.dropbox.upload_service.exception.ResourceNotFoundException;
import com.dropbox.upload_service.exception.UploadLockedException;
import com.dropbox.upload_service.repository.UploadPartRepository;
import com.dropbox.upload_service.repository.UploadSessionRepository;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Completion under the compose-object pattern (see UPL-02): "MinIO multipart
 * complete" becomes a server-side composeObject over the temporary part objects
 * UploadPartService wrote, followed by best-effort cleanup of those temp objects.
 *
 * File/version materialization talks to Metadata Service directly and
 * synchronously - this is the "enough metadata behavior to prove the vertical
 * slice" connection UPL-05 explicitly calls for; the Kafka/Outbox path (EVT-02)
 * that supersedes it is Day 4 scope.
 *
 * Every step is idempotent/resumable from the session's current status, so a
 * repeated or retried call converges on the same result: already-COMPLETED
 * replays; STORAGE_COMPLETED resumes at materialization only; earlier states
 * resume at part verification. Deliberately not wrapped in one @Transactional
 * block, for the same reason as UploadInitiationService: each step here is
 * durably checkpointed on the session's status column one at a time, and a
 * mid-flight failure (MinIO down, metadata-service down) must leave the prior
 * checkpoint intact rather than roll it back.
 *
 * UPL-06: a Redis lock (lock:upload:complete:{uploadId}) wraps the whole
 * method so two Upload Service instances can never simultaneously finalize
 * the same upload - this is an ADDITIONAL correctness layer on top of the
 * state machine/idempotency/DB constraints above, not a replacement for them
 * (see TECHNICAL_DESIGN.md 45/46). If Redis itself is unreachable, the
 * request fails cleanly (DependencyUnavailableException) rather than
 * proceeding uncoordinated; if another request already holds the lock, this
 * one is rejected (UploadLockedException) for the caller to retry shortly.
 */
@Service
@RequiredArgsConstructor
public class UploadCompletionService {

    private static final Set<UploadStatus> RESUMABLE_STATES = Set.of(
            UploadStatus.INITIATED, UploadStatus.UPLOADING, UploadStatus.COMPLETING, UploadStatus.STORAGE_COMPLETED);

    private static final String LOCK_KEY_PREFIX = "lock:upload:complete:";

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadPartRepository uploadPartRepository;
    private final MetadataServiceClient metadataServiceClient;
    private final MinioClient minioClient;
    private final UploadTempPartsCleaner tempPartsCleaner;
    private final RedisLockService lockService;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${upload.completion-lock.ttl-seconds:30}")
    private long lockTtlSeconds;

    public CompleteUploadResponse complete(UUID ownerId, UUID uploadId) {
        String lockKey = LOCK_KEY_PREFIX + uploadId;
        String lockToken = acquireLockOrFail(lockKey);
        try {
            return doComplete(ownerId, uploadId);
        } finally {
            lockService.release(lockKey, lockToken);
        }
    }

    private String acquireLockOrFail(String lockKey) {
        Optional<String> token;
        try {
            token = lockService.tryAcquire(lockKey, Duration.ofSeconds(lockTtlSeconds));
        } catch (RuntimeException e) {
            // Redis unreachable - fail safely rather than blindly finalize uncoordinated.
            throw new DependencyUnavailableException("Distributed lock backend is unavailable", e);
        }
        return token.orElseThrow(() ->
                new UploadLockedException("This upload is already being completed by another request, please retry shortly"));
    }

    private CompleteUploadResponse doComplete(UUID ownerId, UUID uploadId) {
        UploadSession session = uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload not found"));

        if (session.getStatus() == UploadStatus.COMPLETED) {
            return new CompleteUploadResponse(session.getId(), session.getStatus().name(), session.getFileId(), null);
        }

        if (!RESUMABLE_STATES.contains(session.getStatus())) {
            throw new InvalidUploadStateException("Upload cannot be completed from state " + session.getStatus());
        }

        if (session.getStatus() != UploadStatus.STORAGE_COMPLETED) {
            verifyAllPartsPresent(session);

            if (session.getStatus() != UploadStatus.COMPLETING) {
                session.setStatus(UploadStatus.COMPLETING);
                session = uploadSessionRepository.save(session);
            }

            composeInMinio(session);

            session.setStatus(UploadStatus.STORAGE_COMPLETED);
            session = uploadSessionRepository.save(session);

            tempPartsCleaner.cleanup(session);
        }

        MaterializeFileResponse materialized = materializeInMetadata(session);

        session.setStatus(UploadStatus.COMPLETED);
        session.setFileId(materialized.fileId());
        session = uploadSessionRepository.save(session);

        return new CompleteUploadResponse(session.getId(), session.getStatus().name(),
                materialized.fileId(), materialized.versionId());
    }

    private void verifyAllPartsPresent(UploadSession session) {
        Set<Integer> uploaded = new HashSet<>(uploadPartRepository.findPartNumbersByUploadSessionId(session.getId()));
        List<Integer> missing = new ArrayList<>();
        for (int partNumber = 1; partNumber <= session.getTotalParts(); partNumber++) {
            if (!uploaded.contains(partNumber)) {
                missing.add(partNumber);
            }
        }
        if (!missing.isEmpty()) {
            throw new InvalidRequestException("Missing parts: " + missing);
        }
    }

    private void composeInMinio(UploadSession session) {
        List<ComposeSource> sources = new ArrayList<>();
        for (int partNumber = 1; partNumber <= session.getTotalParts(); partNumber++) {
            sources.add(ComposeSource.builder()
                    .bucket(bucket)
                    .object(UploadObjectKeys.partObjectKey(session, partNumber))
                    .build());
        }
        try {
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(bucket)
                    .object(session.getObjectKey())
                    .sources(sources)
                    .build());
        } catch (Exception e) {
            throw new DependencyUnavailableException("Failed to compose parts in storage backend", e);
        }
    }

    private MaterializeFileResponse materializeInMetadata(UploadSession session) {
        if (session.getUploadType() == UploadType.NEW_VERSION) {
            MaterializeVersionRequest request = new MaterializeVersionRequest(
                    session.getId(),
                    session.getFileId(),
                    session.getObjectKey(),
                    session.getTotalSize(),
                    null,
                    null);
            return metadataServiceClient.materializeVersion(session.getUserId(), request);
        }

        MaterializeFileRequest request = new MaterializeFileRequest(
                session.getId(),
                session.getFolderId(),
                session.getFileName(),
                session.getMimeType(),
                session.getObjectKey(),
                session.getTotalSize(),
                null,
                null);
        return metadataServiceClient.materializeFile(session.getUserId(), request);
    }
}
