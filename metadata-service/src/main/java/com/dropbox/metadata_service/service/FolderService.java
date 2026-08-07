package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.Folder;
import com.dropbox.metadata_service.domain.FolderStatus;
import com.dropbox.metadata_service.dto.CreateFolderRequest;
import com.dropbox.metadata_service.dto.UpdateFolderRequest;
import com.dropbox.metadata_service.exception.InvalidRequestException;
import com.dropbox.metadata_service.exception.NameConflictException;
import com.dropbox.metadata_service.exception.ResourceNotFoundException;
import com.dropbox.metadata_service.repository.FolderRepository;
import com.dropbox.metadata_service.repository.FolderSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FolderService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ANCESTOR_HOPS = 1000;

    private final FolderRepository folderRepository;

    @Transactional
    public Folder createFolder(UUID ownerId, CreateFolderRequest request) {
        if (request.parentId() != null) {
            requireActiveOwnedFolder(ownerId, request.parentId());
        }

        assertNameAvailable(ownerId, request.parentId(), request.name(), null);

        Folder folder = Folder.builder()
                .ownerId(ownerId)
                .parentId(request.parentId())
                .name(request.name())
                .status(FolderStatus.ACTIVE)
                .build();

        return folderRepository.save(folder);
    }

    @Transactional(readOnly = true)
    public Folder getFolder(UUID ownerId, UUID folderId) {
        return folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
    }

    @Transactional(readOnly = true)
    public Page<Folder> listChildren(UUID ownerId, UUID parentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, cappedSize(size), Sort.by("name").ascending());
        Specification<Folder> spec = Specification.allOf(
                FolderSpecifications.ownedBy(ownerId),
                FolderSpecifications.hasParent(parentId),
                FolderSpecifications.hasStatus(FolderStatus.ACTIVE)
        );
        return folderRepository.findAll(spec, pageable);
    }

    @Transactional
    public Folder updateFolder(UUID ownerId, UUID folderId, UpdateFolderRequest request) {
        Folder folder = requireActiveOwnedFolder(ownerId, folderId);

        UUID targetParentId = request.parentId() != null ? request.parentId() : folder.getParentId();
        String targetName = request.name() != null ? request.name() : folder.getName();

        boolean parentChanged = !java.util.Objects.equals(targetParentId, folder.getParentId());
        boolean nameChanged = !targetName.equals(folder.getName());

        if (parentChanged) {
            if (targetParentId != null) {
                requireActiveOwnedFolder(ownerId, targetParentId);
                assertNoCycle(folderId, targetParentId, ownerId);
            }
        }

        if (parentChanged || nameChanged) {
            assertNameAvailable(ownerId, targetParentId, targetName, folderId);
        }

        folder.setParentId(targetParentId);
        folder.setName(targetName);
        return folderRepository.save(folder);
    }

    @Transactional
    public void trashFolder(UUID ownerId, UUID folderId) {
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getStatus() == FolderStatus.TRASHED) {
            return;
        }

        folder.setStatus(FolderStatus.TRASHED);
        folder.setDeletedAt(Instant.now());
        folderRepository.save(folder);
    }

    @Transactional
    public Folder restoreFolder(UUID ownerId, UUID folderId) {
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getStatus() == FolderStatus.ACTIVE) {
            return folder;
        }

        assertNameAvailable(ownerId, folder.getParentId(), folder.getName(), folderId);

        folder.setStatus(FolderStatus.ACTIVE);
        folder.setDeletedAt(null);
        return folderRepository.save(folder);
    }

    private Folder requireActiveOwnedFolder(UUID ownerId, UUID folderId) {
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
        if (folder.getStatus() != FolderStatus.ACTIVE) {
            throw new InvalidRequestException("Folder is not active");
        }
        return folder;
    }

    private void assertNameAvailable(UUID ownerId, UUID parentId, String name, UUID excludeFolderId) {
        Specification<Folder> spec = Specification.allOf(
                FolderSpecifications.ownedBy(ownerId),
                FolderSpecifications.hasParent(parentId),
                FolderSpecifications.hasStatus(FolderStatus.ACTIVE),
                FolderSpecifications.hasName(name)
        );
        if (excludeFolderId != null) {
            spec = spec.and(FolderSpecifications.idNot(excludeFolderId));
        }
        if (folderRepository.exists(spec)) {
            throw new NameConflictException("A folder named '" + name + "' already exists in the destination");
        }
    }

    private void assertNoCycle(UUID folderId, UUID destinationParentId, UUID ownerId) {
        UUID current = destinationParentId;
        int hops = 0;
        while (current != null) {
            if (current.equals(folderId)) {
                throw new InvalidRequestException("Cannot move a folder into itself or one of its descendants");
            }
            if (++hops > MAX_ANCESTOR_HOPS) {
                throw new InvalidRequestException("Folder hierarchy too deep to validate move");
            }
            Folder ancestor = folderRepository.findByIdAndOwnerId(current, ownerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Destination folder not found"));
            current = ancestor.getParentId();
        }
    }

    private static int cappedSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
