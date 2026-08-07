package com.dropbox.metadata_service.dto;

import com.dropbox.metadata_service.domain.ShareLink;

import java.time.Instant;
import java.util.UUID;

public record ShareResponse(
        UUID id,
        UUID fileId,
        String permission,
        String status,
        Instant expiresAt,
        Instant createdAt
) {
    public static ShareResponse from(ShareLink share) {
        return new ShareResponse(
                share.getId(),
                share.getFileId(),
                share.getPermission(),
                share.getStatus().name(),
                share.getExpiresAt(),
                share.getCreatedAt()
        );
    }
}
