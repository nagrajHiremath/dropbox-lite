package com.dropbox.metadata_service.dto;

import java.util.List;

/**
 * WRK-02: all storage object keys belonging to a file's versions, for
 * post-permanent-deletion MinIO cleanup. Internal only - objectKey is
 * deliberately never exposed on public DTOs.
 */
public record FileVersionObjectKeysResponse(
        List<String> objectKeys
) {
}
