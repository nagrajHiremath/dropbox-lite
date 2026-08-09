package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.FileEntity;
import com.dropbox.metadata_service.domain.FileStatus;
import com.dropbox.metadata_service.domain.FileVersion;
import com.dropbox.metadata_service.domain.Folder;
import com.dropbox.metadata_service.domain.FolderStatus;
import com.dropbox.metadata_service.dto.MaterializeFileRequest;
import com.dropbox.metadata_service.dto.MaterializeFileResponse;
import com.dropbox.metadata_service.dto.MaterializeVersionRequest;
import com.dropbox.metadata_service.exception.ConflictException;
import com.dropbox.metadata_service.exception.DependencyUnavailableException;
import com.dropbox.metadata_service.exception.InvalidRequestException;
import com.dropbox.metadata_service.exception.ResourceNotFoundException;
import com.dropbox.metadata_service.repository.FileRepository;
import com.dropbox.metadata_service.repository.FileVersionRepository;
import com.dropbox.metadata_service.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Materializes a logical file/version from a completed Upload Service session.
 * This is the "enough metadata behavior to prove the vertical slice" connection
 * UPL-05 calls for - the real EVT-02 Kafka consumer (Day 4) supersedes this path.
 *
 * Idempotent per sourceUploadId: a retried completion call (e.g. after the caller
 * crashed between this call succeeding and it recording that fact) replays the
 * same result instead of creating a second file/version.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileMaterializationService {

    private static final String LOCK_KEY_PREFIX = "lock:file:version:";

    private final FileVersionRepository fileVersionRepository;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FileVersionMaterializer materializer;
    private final RedisLockService lockService;

    @Value("${metadata.version-lock.ttl-seconds:30}")
    private long lockTtlSeconds;

    public MaterializeFileResponse materialize(UUID ownerId, MaterializeFileRequest request) {
        try {
            Optional<FileVersion> existing = fileVersionRepository.findBySourceUploadId(request.sourceUploadId());
            if (existing.isPresent()) {
                MDC.put("fileId", existing.get().getFileId().toString());
                log.debug("materialize: replaying already-materialized version for sourceUploadId {}", request.sourceUploadId());
                return toResponse(existing.get());
            }

            if (request.folderId() != null) {
                Folder folder = folderRepository.findByIdAndOwnerId(request.folderId(), ownerId)
                        .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
                if (folder.getStatus() != FolderStatus.ACTIVE) {
                    throw new InvalidRequestException("Folder is not active");
                }
            }

            FileVersion version;
            try {
                version = materializer.createFileAndVersion(ownerId, request);
            } catch (DataIntegrityViolationException e) {
                // The atomic create rolled back entirely (FileEntity included - see
                // FileVersionMaterializer), so no orphan was left behind. A concurrent
                // request already committed the real version; reload and replay it.
                version = fileVersionRepository.findBySourceUploadId(request.sourceUploadId())
                        .orElseThrow(() -> e);
            }

            MDC.put("fileId", version.getFileId().toString());
            log.info("materialize: created new file/version for sourceUploadId {}", request.sourceUploadId());
            return toResponse(version);
        } finally {
            MDC.remove("fileId");
        }
    }

    /**
     * VER-02: attaches a new version to an existing, owned, ACTIVE file.
     * Idempotent per sourceUploadId like materialize() above. A DataIntegrityViolationException
     * here can mean either this exact completion retried concurrently (source_upload_id
     * collision - replay it) or a different concurrent version upload for the same file
     * won the (file_id, version_number) race - the same protection restoreVersion (VER-03)
     * already relies on. The latter surfaces as 409 for the caller to retry, matching
     * restoreVersion's existing single-attempt-then-409 convention for this table.
     *
     * A Redis lock (lock:file:version:{fileId}, see UPL-06's RedisLockService and
     * TECHNICAL_DESIGN.md 45/46) wraps this whole method as an ADDITIONAL correctness
     * layer on top of the DB unique constraint above, not a replacement for it - two
     * concurrent version uploads for the same file now serialize instead of both racing
     * MinIO/DB work only for one to lose at the last moment. If Redis itself is
     * unreachable, the request fails cleanly rather than proceeding uncoordinated.
     */
    public MaterializeFileResponse materializeVersion(UUID ownerId, MaterializeVersionRequest request) {
        MDC.put("fileId", request.fileId().toString());
        try {
            String lockKey = LOCK_KEY_PREFIX + request.fileId();
            String lockToken = acquireLockOrFail(lockKey);
            try {
                return doMaterializeVersion(ownerId, request);
            } finally {
                lockService.release(lockKey, lockToken);
            }
        } finally {
            MDC.remove("fileId");
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
                new ConflictException("A version is already being created for this file, please retry shortly"));
    }

    private MaterializeFileResponse doMaterializeVersion(UUID ownerId, MaterializeVersionRequest request) {
        Optional<FileVersion> existing = fileVersionRepository.findBySourceUploadId(request.sourceUploadId());
        if (existing.isPresent()) {
            log.debug("materializeVersion: replaying already-materialized version for sourceUploadId {}", request.sourceUploadId());
            return toResponse(existing.get());
        }

        FileEntity file = fileRepository.findByIdAndOwnerId(request.fileId(), ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (file.getStatus() != FileStatus.ACTIVE) {
            throw new InvalidRequestException("File is not active");
        }

        try {
            FileVersion version = materializer.createNextVersion(file, request);
            log.info("materializeVersion: created new version {} for sourceUploadId {}", version.getVersionNumber(), request.sourceUploadId());
            return toResponse(version);
        } catch (DataIntegrityViolationException e) {
            return fileVersionRepository.findBySourceUploadId(request.sourceUploadId())
                    .map(this::toResponse)
                    .orElseThrow(() -> new ConflictException("Concurrent version creation detected, please retry"));
        }
    }

    private MaterializeFileResponse toResponse(FileVersion version) {
        return new MaterializeFileResponse(version.getFileId(), version.getId(), version.getVersionNumber());
    }
}
