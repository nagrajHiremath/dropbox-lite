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
import com.dropbox.metadata_service.exception.InvalidRequestException;
import com.dropbox.metadata_service.exception.ResourceNotFoundException;
import com.dropbox.metadata_service.repository.FileRepository;
import com.dropbox.metadata_service.repository.FileVersionRepository;
import com.dropbox.metadata_service.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
@Service
@RequiredArgsConstructor
public class FileMaterializationService {

    private final FileVersionRepository fileVersionRepository;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FileVersionMaterializer materializer;

    public MaterializeFileResponse materialize(UUID ownerId, MaterializeFileRequest request) {
        Optional<FileVersion> existing = fileVersionRepository.findBySourceUploadId(request.sourceUploadId());
        if (existing.isPresent()) {
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

        return toResponse(version);
    }

    /**
     * VER-02: attaches a new version to an existing, owned, ACTIVE file.
     * Idempotent per sourceUploadId like materialize() above. A DataIntegrityViolationException
     * here can mean either this exact completion retried concurrently (source_upload_id
     * collision - replay it) or a different concurrent version upload for the same file
     * won the (file_id, version_number) race - the same protection restoreVersion (VER-03)
     * already relies on. The latter surfaces as 409 for the caller to retry, matching
     * restoreVersion's existing single-attempt-then-409 convention for this table.
     */
    public MaterializeFileResponse materializeVersion(UUID ownerId, MaterializeVersionRequest request) {
        Optional<FileVersion> existing = fileVersionRepository.findBySourceUploadId(request.sourceUploadId());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        FileEntity file = fileRepository.findByIdAndOwnerId(request.fileId(), ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (file.getStatus() != FileStatus.ACTIVE) {
            throw new InvalidRequestException("File is not active");
        }

        try {
            FileVersion version = materializer.createNextVersion(file, request);
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
