package com.dropbox.upload_service.controller;

import com.dropbox.upload_service.dto.CompleteUploadResponse;
import com.dropbox.upload_service.dto.InitiateUploadRequest;
import com.dropbox.upload_service.dto.InitiateUploadResponse;
import com.dropbox.upload_service.dto.UploadPartResponse;
import com.dropbox.upload_service.dto.UploadStatusResponse;
import com.dropbox.upload_service.service.UploadAbortService;
import com.dropbox.upload_service.service.UploadCompletionService;
import com.dropbox.upload_service.service.UploadInitiationService;
import com.dropbox.upload_service.service.UploadPartService;
import com.dropbox.upload_service.service.UploadStatusService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadInitiationService uploadInitiationService;
    private final UploadPartService uploadPartService;
    private final UploadStatusService uploadStatusService;
    private final UploadCompletionService uploadCompletionService;
    private final UploadAbortService uploadAbortService;

    @PostMapping
    public ResponseEntity<InitiateUploadResponse> initiateUpload(
            @AuthenticationPrincipal UUID ownerId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InitiateUploadRequest request) {
        InitiateUploadResponse response = uploadInitiationService.initiate(ownerId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{uploadId}/parts/{partNumber}")
    public ResponseEntity<UploadPartResponse> uploadPart(
            @AuthenticationPrincipal UUID ownerId,
            @PathVariable UUID uploadId,
            @PathVariable int partNumber,
            HttpServletRequest request) throws IOException {
        // Raw servlet input stream, read directly - no @RequestBody buffering of the part.
        UploadPartResponse response = uploadPartService.uploadPart(ownerId, uploadId, partNumber, request.getInputStream());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{uploadId}")
    public ResponseEntity<UploadStatusResponse> getUploadStatus(
            @AuthenticationPrincipal UUID ownerId,
            @PathVariable UUID uploadId) {
        UploadStatusResponse response = uploadStatusService.getStatus(ownerId, uploadId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<CompleteUploadResponse> completeUpload(
            @AuthenticationPrincipal UUID ownerId,
            @PathVariable UUID uploadId) {
        CompleteUploadResponse response = uploadCompletionService.complete(ownerId, uploadId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<Void> abortUpload(
            @AuthenticationPrincipal UUID ownerId,
            @PathVariable UUID uploadId) {
        uploadAbortService.abort(ownerId, uploadId);
        return ResponseEntity.noContent().build();
    }
}
