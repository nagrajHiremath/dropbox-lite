package com.dropbox.metadata_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFileRequest(
        @NotBlank @Size(max = 255) String name
) {
}
