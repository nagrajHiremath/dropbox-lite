package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.FileEntity;
import com.dropbox.metadata_service.domain.FileStatus;
import com.dropbox.metadata_service.domain.FileVersion;
import com.dropbox.metadata_service.domain.ShareLink;
import com.dropbox.metadata_service.domain.ShareStatus;
import com.dropbox.metadata_service.dto.CreateShareRequest;
import com.dropbox.metadata_service.dto.FileContentInfoResponse;
import com.dropbox.metadata_service.event.ShareEventData;
import com.dropbox.metadata_service.event.ShareRevokedEventData;
import com.dropbox.metadata_service.exception.ConflictException;
import com.dropbox.metadata_service.exception.InvalidRequestException;
import com.dropbox.metadata_service.exception.ResourceNotFoundException;
import com.dropbox.metadata_service.repository.FileRepository;
import com.dropbox.metadata_service.repository.FileVersionRepository;
import com.dropbox.metadata_service.repository.ShareLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_ATTEMPTS = 3;
    private static final String SHARE_KEY_PREFIX = "share:";

    private final ShareLinkRepository shareLinkRepository;
    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final RedisCacheService cacheService;

    @Value("${metadata.cache.ttl-seconds:300}")
    private long cacheTtlSeconds;

    @Transactional
    public RawShare createShare(UUID ownerId, UUID fileId, CreateShareRequest request) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (file.getStatus() != FileStatus.ACTIVE) {
            throw new InvalidRequestException("File is not active");
        }

        for (int attempt = 1; attempt <= MAX_TOKEN_ATTEMPTS; attempt++) {
            String rawToken = generateToken();
            ShareLink share = ShareLink.builder()
                    .fileId(fileId)
                    .tokenHash(hashToken(rawToken))
                    .permission(request.permission())
                    .status(ShareStatus.ACTIVE)
                    .expiresAt(request.expiresAt())
                    .createdBy(ownerId)
                    .build();
            try {
                share = shareLinkRepository.save(share);
                outboxEventWriter.enqueue("FILE", fileId, "FILE_SHARED", ownerId,
                        new ShareEventData(share.getId(), fileId, share.getPermission(), share.getExpiresAt()));
                return new RawShare(share, rawToken);
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_TOKEN_ATTEMPTS) {
                    throw new ConflictException("Failed to generate a unique share token, please retry");
                }
            }
        }
        throw new ConflictException("Failed to generate a unique share token, please retry");
    }

    @Transactional(readOnly = true)
    public List<ShareLink> listShares(UUID ownerId, UUID fileId) {
        fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        return shareLinkRepository.findByFileIdOrderByCreatedAtDesc(fileId);
    }

    @Transactional
    public void revokeShare(UUID ownerId, UUID shareId) {
        ShareLink share = shareLinkRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found"));

        FileEntity file = fileRepository.findByIdAndOwnerId(share.getFileId(), ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found"));

        if (share.getStatus() == ShareStatus.REVOKED) {
            return;
        }

        share.setStatus(ShareStatus.REVOKED);
        shareLinkRepository.save(share);
        cacheService.evict(SHARE_KEY_PREFIX + share.getTokenHash());

        outboxEventWriter.enqueue("FILE", share.getFileId(), "SHARE_REVOKED", ownerId,
                new ShareRevokedEventData(share.getId(), share.getFileId()));
    }

    @Transactional(readOnly = true)
    public PublicShareView resolvePublicShare(String rawToken) {
        ActiveShare active = resolveActiveShare(rawToken);
        FileEntity file = active.file();

        Long sizeBytes = file.getCurrentVersionId() == null ? null
                : fileVersionRepository.findById(file.getCurrentVersionId())
                        .map(FileVersion::getSizeBytes)
                        .orElse(null);

        return new PublicShareView(file, active.share().getPermission(), sizeBytes);
    }

    /**
     * SHR-02 content: same token/ACTIVE/expiry/file-ACTIVE validation as
     * resolvePublicShare above, plus the current version's storage reference -
     * objectKey is deliberately never exposed on PublicShareResponse, so this is
     * service-to-service only (Download Service calls it directly, see
     * InternalPublicShareController).
     */
    @Transactional(readOnly = true)
    public FileContentInfoResponse resolvePublicShareContent(String rawToken) {
        ActiveShare active = resolveActiveShare(rawToken);
        FileEntity file = active.file();

        if (file.getCurrentVersionId() == null) {
            throw new ResourceNotFoundException("Share not found");
        }
        FileVersion version = fileVersionRepository.findById(file.getCurrentVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("Share not found"));

        return new FileContentInfoResponse(file.getName(), file.getMimeType(), version.getObjectKey(), version.getSizeBytes());
    }

    /**
     * RDS-02: cache-aside on share:{tokenHash}. The ACTIVE-status and expiry
     * checks below run against the ShareLink regardless of whether it came
     * from cache or DB - a cache hit never skips them, so "Expiry/status
     * still checked" holds even when Redis serves the read.
     */
    private ActiveShare resolveActiveShare(String rawToken) {
        String tokenHash = hashToken(rawToken);
        String cacheKey = SHARE_KEY_PREFIX + tokenHash;

        Optional<ShareLink> cached = cacheService.get(cacheKey, ShareLink.class);
        ShareLink share = cached.orElseGet(() -> {
            ShareLink loaded = shareLinkRepository.findByTokenHash(tokenHash)
                    .orElseThrow(() -> new ResourceNotFoundException("Share not found"));
            cacheService.put(cacheKey, loaded, Duration.ofSeconds(cacheTtlSeconds));
            return loaded;
        });

        if (share.getStatus() != ShareStatus.ACTIVE) {
            throw new ResourceNotFoundException("Share not found");
        }
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(Instant.now())) {
            throw new ResourceNotFoundException("Share not found");
        }

        FileEntity file = fileRepository.findById(share.getFileId())
                .orElseThrow(() -> new ResourceNotFoundException("Share not found"));
        if (file.getStatus() != FileStatus.ACTIVE) {
            throw new ResourceNotFoundException("Share not found");
        }

        return new ActiveShare(share, file);
    }

    private record ActiveShare(ShareLink share, FileEntity file) {
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

    public record RawShare(ShareLink share, String rawToken) {
    }

    public record PublicShareView(FileEntity file, String permission, Long sizeBytes) {
    }
}
