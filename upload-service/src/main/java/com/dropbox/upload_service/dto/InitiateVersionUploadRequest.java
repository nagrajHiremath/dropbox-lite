package com.dropbox.upload_service.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * VER-01. No fileName/folderId here - a new version keeps the existing file's
 * name and location (renaming/moving is META-05's job); mimeType is optional
 * and falls back to the existing file's mimeType if omitted.
 */
public record InitiateVersionUploadRequest(
        @Positive long size,
        @Size(max = 255) String mimeType
) {
}
