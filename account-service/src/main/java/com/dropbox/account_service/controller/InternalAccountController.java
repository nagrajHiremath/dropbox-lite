package com.dropbox.account_service.controller;

import com.dropbox.account_service.domain.StorageQuota;
import com.dropbox.account_service.dto.StorageUsageResponse;
import com.dropbox.account_service.repository.StorageQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service only (no gateway route exists for /api/v1/internal/**,
 * see api-gateway's application.yaml) - upload-service calls this directly
 * to enforce quota at upload-initiation time. Unlike metadata-service's
 * InternalFileController, identity is already fully carried by the {userId}
 * path variable (a storage_quotas row's PK), so there's no separate ownerId
 * to authenticate - exempted from JwtAuthenticationFilter in SecurityConfig,
 * the same way metadata-service exempts its own no-caller-identity internal
 * endpoints.
 */
@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
public class InternalAccountController {

    private final StorageQuotaRepository storageQuotaRepository;

    @GetMapping("/{userId}/storage")
    public ResponseEntity<StorageUsageResponse> getStorageUsage(@PathVariable UUID userId) {
        StorageQuota quota = storageQuotaRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("No storage quota found for user " + userId));
        return ResponseEntity.ok(new StorageUsageResponse(quota.getUsedBytes(), quota.getMaxBytes()));
    }
}
