package com.dropbox.upload_service.dto;

import java.util.UUID;

/**
 * Mirrors metadata-service's internal MaterializeFileRequest contract
 * (POST /api/v1/internal/files). Kept as upload-service's own copy rather than
 * a shared library, per this project's "no shared business-domain library" rule.
 */
public record MaterializeFileRequest(
        UUID sourceUploadId,
        UUID folderId,
        String fileName,
        String mimeType,
        String objectKey,
        long sizeBytes,
        String checksum,
        String etag
) {
}
