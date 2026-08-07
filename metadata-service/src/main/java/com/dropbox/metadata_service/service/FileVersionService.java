package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.FileEntity;
import com.dropbox.metadata_service.domain.FileVersion;
import com.dropbox.metadata_service.exception.ConflictException;
import com.dropbox.metadata_service.exception.InvalidRequestException;
import com.dropbox.metadata_service.exception.ResourceNotFoundException;
import com.dropbox.metadata_service.repository.FileRepository;
import com.dropbox.metadata_service.repository.FileVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileVersionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final FileVersionRepository fileVersionRepository;
    private final FileRepository fileRepository;

    @Transactional(readOnly = true)
    public Page<FileVersion> listVersions(UUID ownerId, UUID fileId, int page, int size) {
        requireOwnedFile(ownerId, fileId);
        Pageable pageable = PageRequest.of(page, cappedSize(size));
        return fileVersionRepository.findByFileIdOrderByVersionNumberDesc(fileId, pageable);
    }

    @Transactional
    public FileVersion restoreVersion(UUID ownerId, UUID fileId, UUID versionId) {
        FileEntity file = requireOwnedFile(ownerId, fileId);

        FileVersion sourceVersion = fileVersionRepository.findByIdAndFileId(versionId, fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found"));

        FileVersion latest = fileVersionRepository.findTopByFileIdOrderByVersionNumberDesc(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("No versions exist for this file"));

        FileVersion restored = FileVersion.builder()
                .fileId(fileId)
                .versionNumber(latest.getVersionNumber() + 1)
                .objectKey(sourceVersion.getObjectKey())
                .sizeBytes(sourceVersion.getSizeBytes())
                .checksum(sourceVersion.getChecksum())
                .etag(sourceVersion.getEtag())
                .createdBy(ownerId)
                .build();

        try {
            restored = fileVersionRepository.save(restored);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Concurrent version creation detected, please retry");
        }

        file.setCurrentVersionId(restored.getId());
        fileRepository.save(file);

        return restored;
    }

    private FileEntity requireOwnedFile(UUID ownerId, UUID fileId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (file.getStatus() == com.dropbox.metadata_service.domain.FileStatus.DELETED) {
            throw new InvalidRequestException("File has been permanently deleted");
        }
        return file;
    }

    private static int cappedSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
