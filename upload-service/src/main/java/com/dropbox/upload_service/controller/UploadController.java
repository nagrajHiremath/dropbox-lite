package com.dropbox.upload_service.controller;

import com.dropbox.upload_service.dto.InitiateUploadRequest;
import com.dropbox.upload_service.dto.InitiateUploadResponse;
import com.dropbox.upload_service.service.UploadInitiationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadInitiationService uploadInitiationService;

    @PostMapping
    public ResponseEntity<InitiateUploadResponse> initiateUpload(
            @AuthenticationPrincipal UUID ownerId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InitiateUploadRequest request) {
        InitiateUploadResponse response = uploadInitiationService.initiate(ownerId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
