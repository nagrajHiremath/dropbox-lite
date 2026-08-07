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
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static FileResponse from(FileEntity file) {
        return new FileResponse(
                file.getId(),
                file.getOwnerId(),
                file.getFolderId(),
                file.getName(),
                file.getMimeType(),
                file.getCurrentVersionId(),
                file.getStatus().name(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }
}
