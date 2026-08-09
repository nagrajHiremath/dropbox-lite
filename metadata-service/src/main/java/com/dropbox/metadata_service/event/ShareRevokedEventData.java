package com.dropbox.metadata_service.event;

import java.util.UUID;

/**
 * Payload for SHARE_REVOKED (file.lifecycle.v1, OBX-02).
 */
public record ShareRevokedEventData(
        UUID shareId,
        UUID fileId
) {
}
