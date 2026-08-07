package com.dropbox.metadata_service.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record CreateShareRequest(
        @NotBlank String permission,
        Instant expiresAt
) {
}
