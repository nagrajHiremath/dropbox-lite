package com.dropbox.account_service.dto;

import java.time.Instant;
import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String email,
        String displayName,
        Instant createdAt
) {
}
