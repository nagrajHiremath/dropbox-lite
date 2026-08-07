package com.dropbox.metadata_service.dto;

import java.util.UUID;

public record PublicShareResponse(
        UUID fileId,
        String fileName,
        String mimeType,
        Long sizeBytes,
        String permission
) {
}
