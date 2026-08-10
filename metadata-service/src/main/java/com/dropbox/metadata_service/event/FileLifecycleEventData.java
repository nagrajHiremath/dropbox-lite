package com.dropbox.metadata_service.event;

import java.util.UUID;

/**
 * Shared payload shape for FILE_TRASHED / FILE_RESTORED / FILE_PERMANENTLY_DELETED
 * (file.lifecycle.v1, OBX-02). Kept minimal - no version object keys - since
 * WRK-02 (async-worker's MinIO cleanup consumer) looks up versions by fileId
 * itself.
 *
 * totalSizeBytes is only populated for FILE_PERMANENTLY_DELETED - the sum of
 * every version's sizeBytes for this file (WRK-02 deletes all version
 * objects, not just the current one, so this is what account-service's
 * storage-usage consumer needs to subtract from used_bytes). Null for
 * FILE_TRASHED/FILE_RESTORED, which don't change quota usage - a trashed
 * file still counts against quota until it's permanently deleted.
 */
public record FileLifecycleEventData(
        UUID fileId,
        String name,
        UUID folderId,
        Long totalSizeBytes
) {
}
