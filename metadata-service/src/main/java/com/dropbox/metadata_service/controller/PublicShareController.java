package com.dropbox.metadata_service.controller;

import com.dropbox.metadata_service.dto.PublicShareResponse;
import com.dropbox.metadata_service.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/shares")
@RequiredArgsConstructor
public class PublicShareController {

    private final ShareService shareService;

    @GetMapping("/{token}")
    public ResponseEntity<PublicShareResponse> getPublicShare(@PathVariable String token) {
        ShareService.PublicShareView view = shareService.resolvePublicShare(token);
        PublicShareResponse response = new PublicShareResponse(
                view.file().getId(),
                view.file().getName(),
                view.file().getMimeType(),
                view.sizeBytes(),
                view.permission()
        );
        return ResponseEntity.ok(response);
    }
}
