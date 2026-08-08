package com.dropbox.metadata_service.controller;

import com.dropbox.metadata_service.dto.FileContentInfoResponse;
import com.dropbox.metadata_service.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only (no gateway route exists for /api/v1/internal/**).
 * Unlike InternalFileController's endpoints, this one carries no ownerId - a
 * public share (SHR-02) isn't scoped to a caller's identity, only to a valid
 * token - so it's exempted from CurrentUserHeaderFilter's authentication
 * requirement in SecurityConfig, the same way /api/v1/public/** is.
 */
@RestController
@RequestMapping("/api/v1/internal/public-shares")
@RequiredArgsConstructor
public class InternalPublicShareController {

    private final ShareService shareService;

    @GetMapping("/{token}/content")
    public ResponseEntity<FileContentInfoResponse> content(@PathVariable String token) {
        FileContentInfoResponse response = shareService.resolvePublicShareContent(token);
        return ResponseEntity.ok(response);
    }
}
