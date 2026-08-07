package com.dropbox.metadata_service.controller;

import com.dropbox.metadata_service.domain.ShareLink;
import com.dropbox.metadata_service.dto.CreateShareRequest;
import com.dropbox.metadata_service.dto.ShareCreatedResponse;
import com.dropbox.metadata_service.dto.ShareResponse;
import com.dropbox.metadata_service.service.ShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files/{fileId}/shares")
@RequiredArgsConstructor
public class FileShareController {

    private final ShareService shareService;

    @PostMapping
    public ResponseEntity<ShareCreatedResponse> createShare(@AuthenticationPrincipal UUID ownerId,
                                                              @PathVariable UUID fileId,
                                                              @Valid @RequestBody CreateShareRequest request) {
        ShareService.RawShare raw = shareService.createShare(ownerId, fileId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ShareCreatedResponse.from(raw.share(), raw.rawToken()));
    }

    @GetMapping
    public ResponseEntity<List<ShareResponse>> listShares(@AuthenticationPrincipal UUID ownerId,
                                                            @PathVariable UUID fileId) {
        List<ShareLink> shares = shareService.listShares(ownerId, fileId);
        return ResponseEntity.ok(shares.stream().map(ShareResponse::from).toList());
    }
}
