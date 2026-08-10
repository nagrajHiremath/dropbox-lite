package com.dropbox.account_service.controller;

import com.dropbox.account_service.domain.StorageQuota;
import com.dropbox.account_service.dto.StorageUsageResponse;
import com.dropbox.account_service.repository.StorageQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Routed by api-gateway's existing account-service predicate
 * (/api/v1/users/**, see application.yaml) - no gateway change needed.
 * JwtAuthenticationFilter already sets the authenticated principal to the
 * JWT subject (a user id string), same as AuthController's flow.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class AccountController {

    private final StorageQuotaRepository storageQuotaRepository;

    @GetMapping("/storage")
    public ResponseEntity<StorageUsageResponse> getStorageUsage(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        StorageQuota quota = storageQuotaRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("No storage quota found for user " + userId));
        return ResponseEntity.ok(new StorageUsageResponse(quota.getUsedBytes(), quota.getMaxBytes()));
    }
}
