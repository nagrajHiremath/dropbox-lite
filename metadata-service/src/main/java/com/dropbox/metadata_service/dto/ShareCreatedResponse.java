package com.dropbox.metadata_service.dto;

import com.dropbox.metadata_service.domain.ShareLink;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned only from share creation. The raw token is shown exactly once;
 * only its hash is persisted (see ShareLink.tokenHash).
 */
public record ShareCreatedResponse(
        UUID id,
        UUID fileId,
        String token,
        String permission,
        String status,
        Instant expiresAt,
        Instant createdAt
) {
    public static ShareCreatedResponse from(ShareLink share, String rawToken) {
        return new ShareCreatedResponse(
                share.getId(),
                share.getFileId(),
                rawToken,
                share.getPermission(),
                share.getStatus().name(),
                share.getExpiresAt(),
                share.getCreatedAt()
        );
    }
}
