package com.dropbox.metadata_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateFolderRequest(
        @NotBlank @Size(max = 255) String name,
        UUID parentId
) {
}
