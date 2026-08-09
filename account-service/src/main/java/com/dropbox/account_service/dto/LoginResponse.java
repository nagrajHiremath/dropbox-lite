package com.dropbox.account_service.dto;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        UUID userId,
        String email,
        String refreshToken
) {
}
