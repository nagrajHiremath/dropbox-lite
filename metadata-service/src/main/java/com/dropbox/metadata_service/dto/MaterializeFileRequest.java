package com.dropbox.metadata_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * Internal, service-to-service request used by Upload Service to materialize a
 * logical file/version once a multipart upload completes (see UPL-05). Not part
 * of the public API surface - there is no gateway route for /api/v1/internal/**.
 */
public record MaterializeFileRequest(
        @NotNull UUID sourceUploadId,
        UUID folderId,
        @NotBlank String fileName,
        String mimeType,
        @NotBlank String objectKey,
        @PositiveOrZero long sizeBytes,
        String checksum,
        String etag
) {
}
