package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.FileEntity;
import com.dropbox.metadata_service.domain.FileStatus;
import com.dropbox.metadata_service.domain.Folder;
import com.dropbox.metadata_service.dto.MoveFileRequest;
import com.dropbox.metadata_service.dto.UpdateFileRequest;
import com.dropbox.metadata_service.event.FileLifecycleEventData;
import com.dropbox.metadata_service.exception.InvalidRequestException;
import com.dropbox.metadata_service.exception.NameConflictException;
import com.dropbox.metadata_service.exception.ResourceNotFoundException;
import com.dropbox.metadata_service.repository.FileRepository;
import com.dropbox.metadata_service.repository.FileSpecifications;
import com.dropbox.metadata_service.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_TYPES = Set.of("image", "video");

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final OutboxEventWriter outboxEventWriter;

    @Transactional(readOnly = true)
    public FileEntity getFile(UUID ownerId, UUID fileId) {
        return fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }

    @Transactional(readOnly = true)
    public Page<FileEntity> listFiles(UUID ownerId, UUID folderId, String view, String type, String status,
                                       int page, int size) {
        boolean trashed = "trashed".equalsIgnoreCase(status);
        boolean recent = "recent".equalsIgnoreCase(view);

        if (type != null && !ALLOWED_TYPES.contains(type.toLowerCase())) {
            throw new InvalidRequestException("Unsupported type filter: " + type);
        }

        Specification<FileEntity> spec = Specification.allOf(
                FileSpecifications.ownedBy(ownerId),
                FileSpecifications.hasStatus(trashed ? FileStatus.TRASHED : FileStatus.ACTIVE)
        );

        // Photos/Videos (type filter) are account-wide views, same as Recent/Trash -
        // see TECHNICAL_DESIGN.md's Views API list, which groups ?type=image/video
        // with ?view=recent, not with folder browsing.
        if (!recent && !trashed && type == null) {
            spec = spec.and(FileSpecifications.inFolder(folderId));
        }

        if (type != null) {
            spec = spec.and(FileSpecifications.mimeTypeStartsWith(type.toLowerCase()));
        }

        Pageable pageable = PageRequest.of(page, cappedSize(size), Sort.by("updatedAt").descending());
        return fileRepository.findAll(spec, pageable);
    }

    @Transactional
    public FileEntity renameFile(UUID ownerId, UUID fileId, UpdateFileRequest request) {
        FileEntity file = requireActiveOwnedFile(ownerId, fileId);
        assertNameAvailable(ownerId, file.getFolderId(), request.name(), fileId);
        file.setName(request.name());
        return fileRepository.save(file);
    }

    @Transactional
    public FileEntity moveFile(UUID ownerId, UUID fileId, MoveFileRequest request) {
        FileEntity file = requireActiveOwnedFile(ownerId, fileId);

        if (request.folderId() != null) {
            Folder destination = folderRepository.findByIdAndOwnerId(request.folderId(), ownerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Destination folder not found"));
            if (destination.getStatus() != com.dropbox.metadata_service.domain.FolderStatus.ACTIVE) {
                throw new InvalidRequestException("Destination folder is not active");
            }
        }

        assertNameAvailable(ownerId, request.folderId(), file.getName(), fileId);

        file.setFolderId(request.folderId());
        return fileRepository.save(file);
    }

    @Transactional
    public void trashFile(UUID ownerId, UUID fileId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getStatus() == FileStatus.TRASHED) {
            return;
        }
        if (file.getStatus() == FileStatus.DELETED) {
            throw new InvalidRequestException("File has been permanently deleted");
        }

        file.setStatus(FileStatus.TRASHED);
        file.setDeletedAt(Instant.now());
        fileRepository.save(file);

        outboxEventWriter.enqueue("FILE", fileId, "FILE_TRASHED", ownerId,
                new FileLifecycleEventData(fileId, file.getName(), file.getFolderId()));
    }

    @Transactional
    public FileEntity restoreFile(UUID ownerId, UUID fileId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getStatus() == FileStatus.ACTIVE) {
            return file;
        }
        if (file.getStatus() == FileStatus.DELETED) {
            throw new InvalidRequestException("File has been permanently deleted and cannot be restored");
        }

        file.setStatus(FileStatus.ACTIVE);
        file.setDeletedAt(null);
        file = fileRepository.save(file);

        outboxEventWriter.enqueue("FILE", fileId, "FILE_RESTORED", ownerId,
                new FileLifecycleEventData(fileId, file.getName(), file.getFolderId()));

        return file;
    }

    /**
     * Marks the file's metadata lifecycle as permanently deleted and enqueues
     * FILE_PERMANENTLY_DELETED (OBX-02). Physical MinIO object cleanup is a
     * separate async consumer of that event (WRK-02) and not implemented here.
     */
    @Transactional
    public void permanentlyDeleteFile(UUID ownerId, UUID fileId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        if (file.getStatus() == FileStatus.DELETED) {
            return;
        }

        file.setStatus(FileStatus.DELETED);
        if (file.getDeletedAt() == null) {
            file.setDeletedAt(Instant.now());
        }
        fileRepository.save(file);

        outboxEventWriter.enqueue("FILE", fileId, "FILE_PERMANENTLY_DELETED", ownerId,
                new FileLifecycleEventData(fileId, file.getName(), file.getFolderId()));
    }

    private void assertNameAvailable(UUID ownerId, UUID folderId, String name, UUID excludeFileId) {
        Specification<FileEntity> spec = Specification.allOf(
                FileSpecifications.ownedBy(ownerId),
                FileSpecifications.inFolder(folderId),
                FileSpecifications.hasStatus(FileStatus.ACTIVE),
                FileSpecifications.hasName(name)
        );
        if (excludeFileId != null) {
            spec = spec.and(FileSpecifications.idNot(excludeFileId));
        }
        if (fileRepository.exists(spec)) {
            throw new NameConflictException("A file named '" + name + "' already exists in the destination");
        }
    }

    private FileEntity requireActiveOwnedFile(UUID ownerId, UUID fileId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (file.getStatus() != FileStatus.ACTIVE) {
            throw new InvalidRequestException("File is not active");
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
