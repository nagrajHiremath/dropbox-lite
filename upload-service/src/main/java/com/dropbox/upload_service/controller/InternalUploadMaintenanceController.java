package com.dropbox.upload_service.controller;

import com.dropbox.upload_service.dto.ExpireSweepResponse;
import com.dropbox.upload_service.service.ExpiredUploadCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only (no gateway route exists for /api/v1/internal/**).
 * Triggered by Async Worker's scheduled sweep (see TECHNICAL_DESIGN.md 6.6:
 * "Expired upload cleanup" is a Worker responsibility) - the actual cleanup
 * logic stays here since Upload Service owns upload_sessions/upload_parts and
 * no service may query another service's tables directly. Still goes through
 * the same trusted X-User-Id authentication as every other internal endpoint;
 * the sweep is not scoped to a single owner, so the header's value is not
 * used by the cleanup logic itself.
 */
@RestController
@RequestMapping("/api/v1/internal/uploads")
@RequiredArgsConstructor
public class InternalUploadMaintenanceController {

    private final ExpiredUploadCleanupService expiredUploadCleanupService;

    @PostMapping("/expire-sweep")
    public ResponseEntity<ExpireSweepResponse> expireSweep() {
        int expiredCount = expiredUploadCleanupService.cleanupExpired();
        return ResponseEntity.ok(new ExpireSweepResponse(expiredCount));
    }
}
