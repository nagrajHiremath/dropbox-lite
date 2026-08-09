package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.Folder;

import java.util.List;

/**
 * RDS-01: concrete cache shape for FolderService.listChildren - Page<Folder>
 * (a Spring Data interface backed by PageImpl) doesn't round-trip through
 * Jackson cleanly, so this is what's actually stored/read from Redis and
 * reconstructed into a PageImpl on a cache hit.
 */
record FolderChildrenPage(
        List<Folder> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
