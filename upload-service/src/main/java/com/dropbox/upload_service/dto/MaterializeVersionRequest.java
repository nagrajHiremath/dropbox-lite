package com.dropbox.upload_service.dto;

import java.util.UUID;

/**
 * Mirrors metadata-service's internal MaterializeVersionRequest contract
 * (POST /api/v1/internal/files/versions). Kept as upload-service's own copy
 * rather than a shared library, per this project's "no shared business-domain
 * library" rule.
 */
public record MaterializeVersionRequest(
        UUID sourceUploadId,
        UUID fileId,
        String objectKey,
        long sizeBytes,
        String checksum,
        String etag
) {
}
