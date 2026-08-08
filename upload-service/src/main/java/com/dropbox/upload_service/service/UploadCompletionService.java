package com.dropbox.upload_service.service;

import com.dropbox.upload_service.client.MetadataServiceClient;
import com.dropbox.upload_service.domain.UploadSession;
import com.dropbox.upload_service.domain.UploadStatus;
import com.dropbox.upload_service.dto.CompleteUploadResponse;
import com.dropbox.upload_service.dto.MaterializeFileRequest;
import com.dropbox.upload_service.dto.MaterializeFileResponse;
import com.dropbox.upload_service.exception.DependencyUnavailableException;
import com.dropbox.upload_service.exception.InvalidRequestException;
import com.dropbox.upload_service.exception.InvalidUploadStateException;
import com.dropbox.upload_service.exception.ResourceNotFoundException;
import com.dropbox.upload_service.repository.UploadPartRepository;
import com.dropbox.upload_service.repository.UploadSessionRepository;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.MinioClient;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadCompletionService {

    private static final Set<UploadStatus> RESUMABLE_STATES = Set.of(
            UploadStatus.INITIATED, UploadStatus.UPLOADING, UploadStatus.COMPLETING, UploadStatus.STORAGE_COMPLETED);

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadPartRepository uploadPartRepository;
    private final MetadataServiceClient metadataServiceClient;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public CompleteUploadResponse complete(UUID ownerId, UUID uploadId) {
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

            cleanupTempParts(session);
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

    /**
     * Best-effort: the final object is already composed and durably recorded
     * (STORAGE_COMPLETED) by the time this runs, so a cleanup failure here must
     * not fail the request - it would just leave harmless orphaned temp objects.
     */
    private void cleanupTempParts(UploadSession session) {
        try {
            List<DeleteObject> toDelete = new ArrayList<>();
            for (int partNumber = 1; partNumber <= session.getTotalParts(); partNumber++) {
                toDelete.add(new DeleteObject(UploadObjectKeys.partObjectKey(session, partNumber)));
            }
            Iterable<Result<DeleteError>> results = minioClient.removeObjects(
                    RemoveObjectsArgs.builder().bucket(bucket).objects(toDelete).build());
            for (Result<DeleteError> result : results) {
                result.get(); // force evaluation to actually issue the delete
            }
        } catch (Exception e) {
            log.warn("Failed to clean up temporary parts for upload {}: {}", session.getId(), e.getMessage());
        }
    }

    private MaterializeFileResponse materializeInMetadata(UploadSession session) {
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
