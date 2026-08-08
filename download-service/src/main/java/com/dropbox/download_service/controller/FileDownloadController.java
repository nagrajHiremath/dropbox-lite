package com.dropbox.download_service.controller;

import com.dropbox.download_service.service.DownloadService;
import com.dropbox.download_service.service.DownloadService.ResolvedFile;
import com.dropbox.download_service.web.DownloadResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileDownloadController {

    private final DownloadService downloadService;
    private final DownloadResponseBuilder responseBuilder;

    @GetMapping("/{fileId}/content")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @AuthenticationPrincipal UUID ownerId,
            @PathVariable UUID fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        ResolvedFile file = downloadService.resolve(ownerId, fileId);
        return responseBuilder.build(file, rangeHeader);
    }

    /**
     * VER-03: same streaming/Range behavior as the current-version endpoint
     * above, resolved against an arbitrary (not necessarily current) version.
     */
    @GetMapping("/{fileId}/versions/{versionId}/content")
    public ResponseEntity<StreamingResponseBody> downloadVersion(
            @AuthenticationPrincipal UUID ownerId,
            @PathVariable UUID fileId,
            @PathVariable UUID versionId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        ResolvedFile file = downloadService.resolveVersion(ownerId, fileId, versionId);
        return responseBuilder.build(file, rangeHeader);
    }
}
