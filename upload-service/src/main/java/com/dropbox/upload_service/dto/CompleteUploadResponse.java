package com.dropbox.upload_service.dto;

import java.util.UUID;

public record CompleteUploadResponse(
        UUID uploadId,
        String status,
        UUID fileId,
        UUID versionId
) {
}
