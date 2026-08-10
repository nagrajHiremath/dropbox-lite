package com.dropbox.metadata_service.event;

import java.util.UUID;

/**
 * Payload for FILE_CREATED / FILE_VERSION_CREATED events (file.lifecycle.v1)
 * written to this service's own outbox by EVT-02, published by OBX-02.
 * sizeBytes is this specific version's size (not a running total) -
 * account-service's storage-usage consumer adds it to the user's
 * denormalized used_bytes on receipt; see TECHNICAL_DESIGN.md 9.2.
 */
public record FileEventData(
        UUID fileId,
        UUID versionId,
        int versionNumber,
        long sizeBytes
) {
}
