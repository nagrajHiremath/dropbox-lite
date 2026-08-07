package com.dropbox.metadata_service.controller;

import com.dropbox.metadata_service.domain.FileVersion;
import com.dropbox.metadata_service.dto.FileVersionResponse;
import com.dropbox.metadata_service.dto.PageResponse;
import com.dropbox.metadata_service.service.FileVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files/{fileId}/versions")
@RequiredArgsConstructor
public class FileVersionController {

    private final FileVersionService fileVersionService;

    @GetMapping
    public ResponseEntity<PageResponse<FileVersionResponse>> listVersions(@AuthenticationPrincipal UUID ownerId,
                                                                            @PathVariable UUID fileId,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        Page<FileVersion> versions = fileVersionService.listVersions(ownerId, fileId, page, size);
        return ResponseEntity.ok(PageResponse.from(versions, FileVersionResponse::from));
    }

    @PostMapping("/{versionId}/restore")
    public ResponseEntity<FileVersionResponse> restoreVersion(@AuthenticationPrincipal UUID ownerId,
                                                                @PathVariable UUID fileId,
                                                                @PathVariable UUID versionId) {
        FileVersion restored = fileVersionService.restoreVersion(ownerId, fileId, versionId);
        return ResponseEntity.ok(FileVersionResponse.from(restored));
    }
}
