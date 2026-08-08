package com.dropbox.upload_service.controller;

import com.dropbox.upload_service.dto.InitiateUploadResponse;
import com.dropbox.upload_service.dto.InitiateVersionUploadRequest;
import com.dropbox.upload_service.service.UploadInitiationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * VER-01: starts a new-version multipart upload for an existing file. Reuses
 * UploadController's PUT part / GET status / POST complete / DELETE abort
 * endpoints unchanged - they operate on the session by uploadId regardless of
 * uploadType.
 */
@RestController
@RequestMapping("/api/v1/files/{fileId}/uploads")
@RequiredArgsConstructor
public class FileVersionUploadController {

    private final UploadInitiationService uploadInitiationService;

    @PostMapping
    public ResponseEntity<InitiateUploadResponse> initiateVersionUpload(
            @AuthenticationPrincipal UUID ownerId,
            @PathVariable UUID fileId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InitiateVersionUploadRequest request) {
        InitiateUploadResponse response = uploadInitiationService.initiateNewVersion(ownerId, fileId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
