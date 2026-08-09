package com.dropbox.metadata_service.event;

import java.util.UUID;

/**
 * Payload for FILE_CREATED / FILE_VERSION_CREATED events (file.lifecycle.v1)
 * written to this service's own outbox by EVT-02. Published by OBX-02, not
 * implemented yet.
 */
public record FileEventData(
        UUID fileId,
        UUID versionId,
        int versionNumber
) {
}
