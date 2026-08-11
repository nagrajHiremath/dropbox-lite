package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.FileEntity;
import com.dropbox.metadata_service.domain.FileStatus;
import com.dropbox.metadata_service.domain.Folder;
import com.dropbox.metadata_service.domain.FolderStatus;
import com.dropbox.metadata_service.dto.CreateFolderRequest;
import com.dropbox.metadata_service.dto.UpdateFolderRequest;
import com.dropbox.metadata_service.exception.InvalidRequestException;
import com.dropbox.metadata_service.exception.NameConflictException;
import com.dropbox.metadata_service.exception.ResourceNotFoundException;
import com.dropbox.metadata_service.repository.FileRepository;
import com.dropbox.metadata_service.repository.FileSpecifications;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FolderService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ANCESTOR_HOPS = 1000;
    /** Safety cap on trash/restore/permanent-delete cascade size - a folder
     * with more descendants than this fails fast with a clear error rather
     * than processing an unbounded subtree in one request/transaction. */
    private static final int MAX_SUBTREE_SIZE = 5000;
    private static final String FOLDER_CHILDREN_KEY_PREFIX = "folder:children:";

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final FileService fileService;
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

    /**
     * Cascades into every descendant folder and file, not just this folder's
     * own status - previously this only touched the folder itself, silently
     * orphaning everything inside it (invisible in the UI, never reachable
     * from Trash, never cleaned up). Descendant folders are walked and
     * trashed directly; descendant files are trashed via FileService.trashFile
     * one at a time, reusing its existing FILE_TRASHED outbox emission rather
     * than duplicating it here.
     */
    @Transactional
    public void trashFolder(UUID ownerId, UUID folderId) {
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getStatus() == FolderStatus.TRASHED) {
            return;
        }
        if (folder.getStatus() == FolderStatus.DELETED) {
            throw new InvalidRequestException("Folder has been permanently deleted");
        }

        List<Folder> descendants = collectDescendantFolders(ownerId, folderId, FolderStatus.ACTIVE);

        Instant now = Instant.now();
        folder.setStatus(FolderStatus.TRASHED);
        folder.setDeletedAt(now);
        folderRepository.save(folder);

        descendants.forEach(descendant -> {
            descendant.setStatus(FolderStatus.TRASHED);
            descendant.setDeletedAt(now);
        });
        folderRepository.saveAll(descendants);

        for (FileEntity file : filesInSubtree(ownerId, folderId, descendants, FileStatus.ACTIVE)) {
            fileService.trashFile(ownerId, file.getId());
        }

        evictSubtreeCache(ownerId, folder.getParentId(), folderId, descendants);
    }

    /**
     * Symmetric with trashFolder: restores every descendant that was
     * cascaded to TRASHED alongside this folder. Anything already in a
     * different state - e.g. a descendant the user permanently deleted
     * individually from the flat Trash view while this folder was trashed -
     * is deliberately left untouched rather than force-restored or failing
     * the whole operation.
     */
    @Transactional
    public Folder restoreFolder(UUID ownerId, UUID folderId) {
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getStatus() == FolderStatus.ACTIVE) {
            return folder;
        }
        if (folder.getStatus() == FolderStatus.DELETED) {
            throw new InvalidRequestException("Folder has been permanently deleted and cannot be restored");
        }

        assertNameAvailable(ownerId, folder.getParentId(), folder.getName(), folderId);

        List<Folder> descendants = collectDescendantFolders(ownerId, folderId, FolderStatus.TRASHED);

        folder.setStatus(FolderStatus.ACTIVE);
        folder.setDeletedAt(null);
        folder = folderRepository.save(folder);

        descendants.forEach(descendant -> {
            descendant.setStatus(FolderStatus.ACTIVE);
            descendant.setDeletedAt(null);
        });
        folderRepository.saveAll(descendants);

        for (FileEntity file : filesInSubtree(ownerId, folderId, descendants, FileStatus.TRASHED)) {
            fileService.restoreFile(ownerId, file.getId());
        }

        evictSubtreeCache(ownerId, folder.getParentId(), folderId, descendants);

        return folder;
    }

    /**
     * Trash view: account-wide, like FileService.listFiles' status=trashed
     * path - drops the parent/hierarchy scope entirely rather than listing
     * per-folder, matching how trashed items are surfaced flat regardless of
     * where they originally lived. Uncached, mirroring FileService.listFiles
     * (only getFile/listChildren-active are cached today).
     */
    @Transactional(readOnly = true)
    public Page<Folder> listTrashed(UUID ownerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, cappedSize(size), Sort.by("name").ascending());
        Specification<Folder> spec = Specification.allOf(
                FolderSpecifications.ownedBy(ownerId),
                FolderSpecifications.hasStatus(FolderStatus.TRASHED)
        );
        return folderRepository.findAll(spec, pageable);
    }

    /**
     * Mirrors FileService.permanentlyDeleteFile: idempotent no-op if already
     * DELETED, preserves the original trashedAt if one is already set.
     * Cascades into every not-yet-deleted descendant (ACTIVE or TRASHED -
     * same lenient "doesn't require trashing first" allowance
     * FileService.permanentlyDeleteFile already has for individual files).
     * Folders themselves still emit no outbox event (they never have), but
     * every descendant file goes through FileService.permanentlyDeleteFile
     * individually, so each one still gets its own FILE_PERMANENTLY_DELETED
     * event - that's what actually drives async-worker's MinIO cleanup and
     * account-service's quota decrement, which is exactly what was silently
     * skipped before this fix.
     */
    @Transactional
    public void permanentlyDeleteFolder(UUID ownerId, UUID folderId) {
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getStatus() == FolderStatus.DELETED) {
            return;
        }

        List<Folder> descendants = collectLiveDescendantFolders(ownerId, folderId);

        Instant now = Instant.now();
        folder.setStatus(FolderStatus.DELETED);
        if (folder.getDeletedAt() == null) {
            folder.setDeletedAt(now);
        }
        folderRepository.save(folder);

        descendants.forEach(descendant -> {
            descendant.setStatus(FolderStatus.DELETED);
            if (descendant.getDeletedAt() == null) {
                descendant.setDeletedAt(now);
            }
        });
        folderRepository.saveAll(descendants);

        for (FileEntity file : liveFilesInSubtree(ownerId, folderId, descendants)) {
            fileService.permanentlyDeleteFile(ownerId, file.getId());
        }

        evictSubtreeCache(ownerId, folder.getParentId(), folderId, descendants);
    }

    /**
     * BFS walk of folderId's descendant subtree, mirroring assertNoCycle's
     * iterative ancestor-walk style. requiredStatus scopes which descendants
     * are touched: trash-cascade only wants ACTIVE ones, restore-cascade
     * only wants ones that were TRASHED alongside the root. parentId edges
     * survive trash/restore/delete unchanged (only status/deletedAt move),
     * so walking by hasParent stays correct regardless of status.
     */
    private List<Folder> collectDescendantFolders(UUID ownerId, UUID rootFolderId, FolderStatus requiredStatus) {
        List<Folder> result = new ArrayList<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(rootFolderId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            Specification<Folder> spec = Specification.allOf(
                    FolderSpecifications.ownedBy(ownerId),
                    FolderSpecifications.hasParent(current),
                    FolderSpecifications.hasStatus(requiredStatus)
            );
            for (Folder child : folderRepository.findAll(spec)) {
                if (result.size() >= MAX_SUBTREE_SIZE) {
                    throw new InvalidRequestException("Folder hierarchy too large to process");
                }
                result.add(child);
                queue.add(child.getId());
            }
        }
        return result;
    }

    /** Permanent-delete cascade variant: sweeps every not-yet-DELETED
     * descendant regardless of whether it's ACTIVE or TRASHED, since
     * permanentlyDeleteFolder (like FileService.permanentlyDeleteFile)
     * allows deleting directly without trashing first. */
    private List<Folder> collectLiveDescendantFolders(UUID ownerId, UUID rootFolderId) {
        List<Folder> result = new ArrayList<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(rootFolderId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            Specification<Folder> spec = Specification.allOf(
                    FolderSpecifications.ownedBy(ownerId),
                    FolderSpecifications.hasParent(current),
                    FolderSpecifications.statusNot(FolderStatus.DELETED)
            );
            for (Folder child : folderRepository.findAll(spec)) {
                if (result.size() >= MAX_SUBTREE_SIZE) {
                    throw new InvalidRequestException("Folder hierarchy too large to process");
                }
                result.add(child);
                queue.add(child.getId());
            }
        }
        return result;
    }

    private List<FileEntity> filesIn(UUID ownerId, UUID folderId, FileStatus status) {
        Specification<FileEntity> spec = Specification.allOf(
                FileSpecifications.ownedBy(ownerId),
                FileSpecifications.inFolder(folderId),
                FileSpecifications.hasStatus(status)
        );
        return fileRepository.findAll(spec);
    }

    private List<FileEntity> liveFilesIn(UUID ownerId, UUID folderId) {
        Specification<FileEntity> spec = Specification.allOf(
                FileSpecifications.ownedBy(ownerId),
                FileSpecifications.inFolder(folderId),
                FileSpecifications.statusNot(FileStatus.DELETED)
        );
        return fileRepository.findAll(spec);
    }

    private List<FileEntity> filesInSubtree(UUID ownerId, UUID rootFolderId, List<Folder> descendants, FileStatus status) {
        List<FileEntity> files = new ArrayList<>(filesIn(ownerId, rootFolderId, status));
        for (Folder descendant : descendants) {
            files.addAll(filesIn(ownerId, descendant.getId(), status));
        }
        return files;
    }

    private List<FileEntity> liveFilesInSubtree(UUID ownerId, UUID rootFolderId, List<Folder> descendants) {
        List<FileEntity> files = new ArrayList<>(liveFilesIn(ownerId, rootFolderId));
        for (Folder descendant : descendants) {
            files.addAll(liveFilesIn(ownerId, descendant.getId()));
        }
        return files;
    }

    /** Evicts the parent's children-listing cache (the folder itself
     * appearing/disappearing from it) plus every subtree folder's own
     * children-listing cache (their contents just flipped status too). */
    private void evictSubtreeCache(UUID ownerId, UUID parentId, UUID rootFolderId, List<Folder> descendants) {
        evictChildrenCache(ownerId, parentId);
        evictChildrenCache(ownerId, rootFolderId);
        descendants.forEach(descendant -> evictChildrenCache(ownerId, descendant.getId()));
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
