package com.dropbox.upload_service.dto;

import java.util.List;
import java.util.UUID;

public record UploadStatusResponse(
        UUID uploadId,
        String status,
        int totalParts,
        List<Integer> uploadedParts
) {
}
