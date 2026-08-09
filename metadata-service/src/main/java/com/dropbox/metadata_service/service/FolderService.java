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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FolderService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ANCESTOR_HOPS = 1000;
    private static final String FOLDER_CHILDREN_KEY_PREFIX = "folder:children:";

    private final FolderRepository folderRepository;
    private final RedisCacheService cacheService;

    @Value("${metadata.cache.ttl-seconds:300}")
    private long cacheTtlSeconds;

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

        folder = folderRepository.save(folder);
        evictChildrenCache(ownerId, request.parentId());
        return folder;
    }

    @Transactional(readOnly = true)
    public Folder getFolder(UUID ownerId, UUID folderId) {
        return folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
    }

    /**
     * RDS-01: cache-aside on folder:children:{userId}:{parentIdOrRoot}:{page}:{size}.
     * userId is already part of the key, so unlike FileService.getFile there's
     * no cross-owner leakage risk to guard against on a hit.
     */
    @Transactional(readOnly = true)
    public Page<Folder> listChildren(UUID ownerId, UUID parentId, int page, int size) {
        int cappedSize = cappedSize(size);
        String cacheKey = childrenCacheKey(ownerId, parentId, page, cappedSize);

        Optional<FolderChildrenPage> cached = cacheService.get(cacheKey, FolderChildrenPage.class);
        if (cached.isPresent()) {
            FolderChildrenPage c = cached.get();
            return new PageImpl<>(c.content(), PageRequest.of(c.page(), c.size(), Sort.by("name").ascending()), c.totalElements());
        }

        Pageable pageable = PageRequest.of(page, cappedSize, Sort.by("name").ascending());
        Specification<Folder> spec = Specification.allOf(
                FolderSpecifications.ownedBy(ownerId),
                FolderSpecifications.hasParent(parentId),
                FolderSpecifications.hasStatus(FolderStatus.ACTIVE)
        );
        Page<Folder> result = folderRepository.findAll(spec, pageable);

        cacheService.put(cacheKey, new FolderChildrenPage(result.getContent(), page, cappedSize,
                result.getTotalElements(), result.getTotalPages()), Duration.ofSeconds(cacheTtlSeconds));

        return result;
    }

    @Transactional
    public Folder updateFolder(UUID ownerId, UUID folderId, UpdateFolderRequest request) {
        Folder folder = requireActiveOwnedFolder(ownerId, folderId);

        UUID targetParentId = request.parentId() != null ? request.parentId() : folder.getParentId();
        String targetName = request.name() != null ? request.name() : folder.getName();

        boolean parentChanged = !Objects.equals(targetParentId, folder.getParentId());
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

        UUID previousParentId = folder.getParentId();
        folder.setParentId(targetParentId);
        folder.setName(targetName);
        folder = folderRepository.save(folder);

        evictChildrenCache(ownerId, previousParentId);
        if (parentChanged) {
            evictChildrenCache(ownerId, targetParentId);
        }

        return folder;
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

        evictChildrenCache(ownerId, folder.getParentId());
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
        folder = folderRepository.save(folder);

        evictChildrenCache(ownerId, folder.getParentId());

        return folder;
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

    private void evictChildrenCache(UUID ownerId, UUID parentId) {
        cacheService.evictByPattern(FOLDER_CHILDREN_KEY_PREFIX + ownerId + ":" + parentKeySegment(parentId) + ":*");
    }

    private String childrenCacheKey(UUID ownerId, UUID parentId, int page, int size) {
        return FOLDER_CHILDREN_KEY_PREFIX + ownerId + ":" + parentKeySegment(parentId) + ":" + page + ":" + size;
    }

    private static String parentKeySegment(UUID parentId) {
        return parentId == null ? "root" : parentId.toString();
    }
}
