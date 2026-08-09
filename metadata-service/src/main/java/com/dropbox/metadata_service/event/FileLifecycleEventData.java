package com.dropbox.metadata_service.event;

import java.util.UUID;

/**
 * Shared payload shape for FILE_TRASHED / FILE_RESTORED / FILE_PERMANENTLY_DELETED
 * (file.lifecycle.v1, OBX-02). Kept minimal - no version object keys - since a
 * future MinIO-cleanup consumer (WRK-02, not implemented here) can look up
 * versions by fileId itself.
 */
public record FileLifecycleEventData(
        UUID fileId,
        String name,
        UUID folderId
) {
}
