package com.dropbox.download_service.controller;

import com.dropbox.download_service.service.DownloadService;
import com.dropbox.download_service.service.DownloadService.ResolvedFile;
import com.dropbox.download_service.web.DownloadResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * SHR-02: anonymous, no-JWT public share download. token is hashed and
 * validated (ACTIVE, unexpired, file ACTIVE) entirely on metadata-service's
 * side (see InternalPublicShareController); Download Service never sees or
 * needs an ownerId here. Same streaming/Range behavior as
 * FileDownloadController via the shared DownloadResponseBuilder.
 */
@RestController
@RequestMapping("/api/v1/public/shares")
@RequiredArgsConstructor
public class PublicShareDownloadController {

    private final DownloadService downloadService;
    private final DownloadResponseBuilder responseBuilder;

    @GetMapping("/{token}/content")
    public ResponseEntity<StreamingResponseBody> downloadPublicShare(
            @PathVariable String token,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        ResolvedFile file = downloadService.resolvePublicShare(token);
        return responseBuilder.build(file, rangeHeader);
    }
}
