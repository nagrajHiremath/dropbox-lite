package com.dropbox.metadata_service.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for FILE_SHARED (file.lifecycle.v1, OBX-02).
 */
public record ShareEventData(
        UUID shareId,
        UUID fileId,
        String permission,
        Instant expiresAt
) {
}
