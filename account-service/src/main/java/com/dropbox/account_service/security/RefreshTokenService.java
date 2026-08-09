package com.dropbox.account_service.security;

import com.dropbox.account_service.domain.RefreshToken;
import com.dropbox.account_service.exception.InvalidRefreshTokenException;
import com.dropbox.account_service.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Owns refresh-token creation/validation/rotation - account-service is the
 * only service that ever handles a refresh token. Mirrors metadata-service's
 * ShareService raw-token/hash-only-stored pattern: a SecureRandom raw token is
 * returned to the caller once; only its SHA-256 hash is ever persisted.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Transactional
    public String issue(UUID userId) {
        String rawToken = generateToken();
        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hashToken(rawToken))
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .build();
        refreshTokenRepository.save(token);
        return rawToken;
    }

    /**
     * Validates the raw token and revokes it (single-use rotation - the
     * caller is expected to issue() a replacement right after this succeeds),
     * returning the userId it belonged to. A not-found, already-revoked, or
     * expired token all surface as the same InvalidRefreshTokenException -
     * callers shouldn't be able to distinguish "expired" from "reused after
     * rotation" from "garbage input" from the response.
     */
    @Transactional
    public UUID validateAndRotate(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hashToken(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (token.getRevokedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);

        return token.getUserId();
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
