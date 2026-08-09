package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.FileEntity;
import com.dropbox.metadata_service.domain.FileStatus;
import com.dropbox.metadata_service.domain.FileVersion;
import com.dropbox.metadata_service.dto.FileContentInfoResponse;
import com.dropbox.metadata_service.dto.FileVersionObjectKeysResponse;
import com.dropbox.metadata_service.exception.ResourceNotFoundException;
import com.dropbox.metadata_service.repository.FileRepository;
import com.dropbox.metadata_service.repository.FileVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Resolves what Download Service needs to stream a file's current version
 * (see DNL-01): the owner check plus the current version's storage reference.
 * Metadata Service never touches the actual bytes - MinIO is Download
 * Service's job, using the objectKey handed back here.
 */
@Service
@RequiredArgsConstructor
public class FileContentLookupService {

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;

    @Transactional(readOnly = true)
    public FileContentInfoResponse resolveCurrentVersion(UUID ownerId, UUID fileId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getStatus() == FileStatus.DELETED || file.getCurrentVersionId() == null) {
            throw new ResourceNotFoundException("File not found");
        }

        FileVersion version = fileVersionRepository.findById(file.getCurrentVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        return new FileContentInfoResponse(file.getName(), file.getMimeType(), version.getObjectKey(), version.getSizeBytes());
    }

    /**
     * VER-03: resolves an arbitrary (not necessarily current) version's storage
     * reference, for GET /files/{fileId}/versions/{versionId}/content. Mirrors
     * resolveCurrentVersion's DELETED-file exclusion; TRASHED files are still
     * resolvable, same as current-version downloads already allow.
     */
    @Transactional(readOnly = true)
    public FileContentInfoResponse resolveVersion(UUID ownerId, UUID fileId, UUID versionId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getStatus() == FileStatus.DELETED) {
            throw new ResourceNotFoundException("File not found");
        }

        FileVersion version = fileVersionRepository.findByIdAndFileId(versionId, fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found"));

        return new FileContentInfoResponse(file.getName(), file.getMimeType(), version.getObjectKey(), version.getSizeBytes());
    }

    /**
     * WRK-02: all version object keys for a file, for post-permanent-deletion
     * MinIO cleanup. Deliberately no status filter (unlike resolveCurrentVersion/
     * resolveVersion above) - by the time this is called the file is normally
     * already DELETED, and its versions must still be discoverable so their
     * bytes can be cleaned up.
     */
    @Transactional(readOnly = true)
    public FileVersionObjectKeysResponse resolveAllVersionObjectKeys(UUID ownerId, UUID fileId) {
        fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        List<String> objectKeys = fileVersionRepository.findByFileId(fileId).stream()
                .map(FileVersion::getObjectKey)
                .toList();

        return new FileVersionObjectKeysResponse(objectKeys);
    }
}
