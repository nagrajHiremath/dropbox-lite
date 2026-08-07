package com.dropbox.metadata_service.dto;

import java.util.UUID;

public record MoveFileRequest(
        UUID folderId
) {
}
