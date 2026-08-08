package com.dropbox.metadata_service.dto;

/**
 * Internal, service-to-service response for Download Service (see DNL-01) -
 * the minimum needed to stream a file's current version from MinIO. objectKey
 * is an internal storage handle and is deliberately never exposed on the
 * public FileResponse/FileVersionResponse DTOs.
 */
public record FileContentInfoResponse(
        String fileName,
        String mimeType,
        String objectKey,
        long sizeBytes
) {
}
