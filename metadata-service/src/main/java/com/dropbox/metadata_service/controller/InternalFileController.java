package com.dropbox.metadata_service.controller;

import com.dropbox.metadata_service.dto.MaterializeFileRequest;
import com.dropbox.metadata_service.dto.MaterializeFileResponse;
import com.dropbox.metadata_service.service.FileMaterializationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service only (Upload Service calls this directly via Eureka, not
 * through the gateway - there is no gateway route for /api/v1/internal/**).
 * Identity still comes from the trusted X-User-Id header (CurrentUserHeaderFilter);
 * Upload Service sets it to the same ownerId it already derived from its own
 * gateway-set header for the original end-user request.
 */
@RestController
@RequestMapping("/api/v1/internal/files")
@RequiredArgsConstructor
public class InternalFileController {

    private final FileMaterializationService fileMaterializationService;

    @PostMapping
    public ResponseEntity<MaterializeFileResponse> materialize(@AuthenticationPrincipal UUID ownerId,
                                                                 @Valid @RequestBody MaterializeFileRequest request) {
        MaterializeFileResponse response = fileMaterializationService.materialize(ownerId, request);
        return ResponseEntity.ok(response);
    }
}
