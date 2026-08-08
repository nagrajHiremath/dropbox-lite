package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.FileEntity;
import com.dropbox.metadata_service.domain.FileStatus;
import com.dropbox.metadata_service.domain.FileVersion;
import com.dropbox.metadata_service.dto.MaterializeFileRequest;
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
}
