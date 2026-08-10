package com.dropbox.metadata_service.dto;

import com.dropbox.metadata_service.domain.FileEntity;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        UUID id,
        UUID ownerId,
        UUID folderId,
        String name,
        String mimeType,
        UUID currentVersionId,
        Long sizeBytes,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    /** sizeBytes-less overload for responses where the caller hasn't resolved
     * the current version's size (e.g. rename/move/restore) - not worth an
     * extra query on every mutation just to populate a field the frontend
     * only displays in list/get views, which already resolve it. */
    public static FileResponse from(FileEntity file) {
        return from(file, null);
    }

    /**
     * sizeBytes is the current version's size, resolved by the caller.
     * FileService.currentSizesFor batches this for a whole page in one query
     * (WHERE id IN (...)) instead of a per-file lookup - see FileController's
     * listFiles/getFile for how this is populated without N+1.
     */
    public static FileResponse from(FileEntity file, Long sizeBytes) {
        return new FileResponse(
                file.getId(),
                file.getOwnerId(),
                file.getFolderId(),
                file.getName(),
                file.getMimeType(),
                file.getCurrentVersionId(),
                sizeBytes,
                file.getStatus().name(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }
}
