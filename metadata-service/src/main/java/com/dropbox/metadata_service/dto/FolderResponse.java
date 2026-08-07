package com.dropbox.metadata_service.dto;

import com.dropbox.metadata_service.domain.Folder;

import java.time.Instant;
import java.util.UUID;

public record FolderResponse(
        UUID id,
        UUID ownerId,
        UUID parentId,
        String name,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static FolderResponse from(Folder folder) {
        return new FolderResponse(
                folder.getId(),
                folder.getOwnerId(),
                folder.getParentId(),
                folder.getName(),
                folder.getStatus().name(),
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }
}
