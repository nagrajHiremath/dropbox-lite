package com.dropbox.metadata_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * Internal, service-to-service request used by Upload Service to materialize a
 * new version of an EXISTING file once a NEW_VERSION multipart upload completes
 * (see VER-01/VER-02). Distinct from MaterializeFileRequest, which creates a
 * brand-new logical file - this attaches a version to fileId instead.
 */
public record MaterializeVersionRequest(
        @NotNull UUID sourceUploadId,
        @NotNull UUID fileId,
        @NotBlank String objectKey,
        @PositiveOrZero long sizeBytes,
        String checksum,
        String etag
) {
}
