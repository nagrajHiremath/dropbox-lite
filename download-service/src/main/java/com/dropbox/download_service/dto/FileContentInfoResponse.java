package com.dropbox.download_service.dto;

/**
 * Mirrors metadata-service's internal FileContentInfoResponse contract
 * (GET /api/v1/internal/files/{fileId}/current-version). Kept as
 * download-service's own copy rather than a shared library, per this
 * project's "no shared business-domain library" rule.
 */
public record FileContentInfoResponse(
        String fileName,
        String mimeType,
        String objectKey,
        long sizeBytes
) {
}
