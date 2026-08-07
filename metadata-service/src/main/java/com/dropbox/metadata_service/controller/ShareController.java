package com.dropbox.metadata_service.controller;

import com.dropbox.metadata_service.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shares")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @DeleteMapping("/{shareId}")
    public ResponseEntity<Void> revokeShare(@AuthenticationPrincipal UUID ownerId,
                                             @PathVariable UUID shareId) {
        shareService.revokeShare(ownerId, shareId);
        return ResponseEntity.noContent().build();
    }
}
