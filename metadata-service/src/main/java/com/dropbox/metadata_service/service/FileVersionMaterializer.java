package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.FileEntity;
import com.dropbox.metadata_service.domain.FileStatus;
import com.dropbox.metadata_service.domain.FileVersion;
import com.dropbox.metadata_service.dto.MaterializeFileRequest;
import com.dropbox.metadata_service.dto.MaterializeVersionRequest;
import com.dropbox.metadata_service.repository.FileRepository;
import com.dropbox.metadata_service.repository.FileVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Creates the FileEntity and its first FileVersion as one atomic unit: if the
 * version insert violates the source_upload_id uniqueness constraint (a
 * concurrent duplicate materialization), the whole transaction - including the
 * FileEntity insert - rolls back. No orphan, version-less FileEntity can result.
 *
 * Kept as a separate bean so @Transactional actually applies: it only takes
 * effect through the Spring proxy, so FileMaterializationService cannot just
 * call this logic as a private/self-invoked method on itself. This is a single
 * atomic write, not a reserve/work/finalize protocol - by the time a caller sees
 * the failure, the conflicting row is already fully committed, so there is
 * nothing to wait or poll for; the caller can re-read it immediately.
 */
@Component
@RequiredArgsConstructor
class FileVersionMaterializer {

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;

    @Transactional
    public FileVersion createFileAndVersion(UUID ownerId, MaterializeFileRequest request) {
        FileEntity file = FileEntity.builder()
                .ownerId(ownerId)
                .folderId(request.folderId())
                .name(request.fileName())
                .mimeType(request.mimeType())
                .status(FileStatus.ACTIVE)
                .build();
        file = fileRepository.save(file);

        FileVersion version = FileVersion.builder()
                .fileId(file.getId())
                .versionNumber(1)
                .objectKey(request.objectKey())
                .sizeBytes(request.sizeBytes())
                .checksum(request.checksum())
                .etag(request.etag())
                .createdBy(ownerId)
                .sourceUploadId(request.sourceUploadId())
                .build();
        // Flush immediately so a constraint violation surfaces here, synchronously,
        // rather than being deferred to an implicit flush later in this method.
        version = fileVersionRepository.saveAndFlush(version);

        file.setCurrentVersionId(version.getId());
        fileRepository.save(file);

        return version;
    }

    /**
     * Attaches a new version to an EXISTING file (VER-02), rather than creating
     * a new FileEntity. versionNumber is next-after-current-max, protected by
     * file_versions' (file_id, version_number) unique constraint - the same
     * mechanism VER-03's restoreVersion already relies on for this table. A
     * losing concurrent attempt rolls back this transaction entirely (no
     * currentVersionId update survives), so the caller can safely reload and
     * decide how to respond rather than being left with partial state.
     */
    @Transactional
    public FileVersion createNextVersion(FileEntity file, MaterializeVersionRequest request) {
        int nextVersionNumber = fileVersionRepository.findTopByFileIdOrderByVersionNumberDesc(file.getId())
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        FileVersion version = FileVersion.builder()
                .fileId(file.getId())
                .versionNumber(nextVersionNumber)
                .objectKey(request.objectKey())
                .sizeBytes(request.sizeBytes())
                .checksum(request.checksum())
                .etag(request.etag())
                .createdBy(file.getOwnerId())
                .sourceUploadId(request.sourceUploadId())
                .build();
        version = fileVersionRepository.saveAndFlush(version);

        file.setCurrentVersionId(version.getId());
        fileRepository.save(file);

        return version;
    }
}
