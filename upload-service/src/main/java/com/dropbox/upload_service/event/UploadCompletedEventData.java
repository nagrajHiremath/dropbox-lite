package com.dropbox.upload_service.event;

import java.util.UUID;

/**
 * Payload for the UPLOAD_COMPLETED event (storage.lifecycle.v1). Mirrors the
 * union of MaterializeFileRequest/MaterializeVersionRequest's fields - the
 * consumer dispatches on uploadType to the same materialize()/materializeVersion()
 * logic those synchronous requests already use. sourceUploadId isn't
 * duplicated here since it's already the envelope's own aggregateId.
 */
public record UploadCompletedEventData(
        String uploadType,
        UUID fileId,
        UUID folderId,
        String fileName,
        String mimeType,
        String objectKey,
        long sizeBytes
) {
}
