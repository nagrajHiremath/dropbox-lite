package com.dropbox.upload_service.dto;

import java.util.UUID;

public record InitiateUploadResponse(
        UUID uploadId,
        long chunkSize,
        int totalParts,
        String status
) {
}
