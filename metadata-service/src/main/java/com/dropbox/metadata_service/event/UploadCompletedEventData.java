package com.dropbox.metadata_service.event;

import java.util.UUID;

/**
 * Payload of the UPLOAD_COMPLETED event (storage.lifecycle.v1), mirroring
 * upload-service's producer-side copy. uploadType selects which existing
 * materialize()/materializeVersion() path the consumer dispatches to.
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
