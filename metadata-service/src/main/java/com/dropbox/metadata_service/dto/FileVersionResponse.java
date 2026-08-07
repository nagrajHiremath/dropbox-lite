package com.dropbox.metadata_service.dto;

import com.dropbox.metadata_service.domain.FileVersion;

import java.time.Instant;
import java.util.UUID;

public record FileVersionResponse(
        UUID id,
        UUID fileId,
        int versionNumber,
        long sizeBytes,
        String checksum,
        String etag,
        UUID createdBy,
        Instant createdAt
) {
    public static FileVersionResponse from(FileVersion version) {
        return new FileVersionResponse(
                version.getId(),
                version.getFileId(),
                version.getVersionNumber(),
                version.getSizeBytes(),
                version.getChecksum(),
                version.getEtag(),
                version.getCreatedBy(),
                version.getCreatedAt()
        );
    }
}
