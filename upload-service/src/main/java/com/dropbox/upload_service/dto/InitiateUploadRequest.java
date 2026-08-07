package com.dropbox.upload_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record InitiateUploadRequest(
        @NotBlank @Size(max = 255) String fileName,
        UUID folderId,
        @Positive long size,
        @Size(max = 255) String mimeType
) {
}
