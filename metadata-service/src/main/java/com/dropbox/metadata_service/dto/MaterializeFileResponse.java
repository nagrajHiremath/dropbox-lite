package com.dropbox.metadata_service.dto;

import java.util.UUID;

public record MaterializeFileResponse(
        UUID fileId,
        UUID versionId,
        int versionNumber
) {
}
