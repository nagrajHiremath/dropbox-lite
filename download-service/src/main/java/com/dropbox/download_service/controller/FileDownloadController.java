package com.dropbox.download_service.controller;

import com.dropbox.download_service.service.ByteRangeParser;
import com.dropbox.download_service.service.ByteRangeParser.ByteRange;
import com.dropbox.download_service.service.DownloadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileDownloadController {

    private final DownloadService downloadService;

    @GetMapping("/{fileId}/content")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @AuthenticationPrincipal UUID ownerId,
            @PathVariable UUID fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            HttpServletRequest servletRequest) {

        // TEMPORARY diagnostic logging - remove once the DNL-02 Range investigation is closed.
        log.debug("downloadFile: fileId={}, @RequestHeader(RANGE)=[{}], servletRequest.getHeader(\"Range\")=[{}], allHeaderNames={}",
                fileId, rangeHeader, servletRequest.getHeader("Range"),
                Collections.list(servletRequest.getHeaderNames()));

        DownloadService.ResolvedFile file = downloadService.resolve(ownerId, fileId);
        String contentDisposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString();
        MediaType mediaType = resolveMediaType(file.mimeType());

        Optional<ByteRange> range = ByteRangeParser.parse(rangeHeader, file.sizeBytes());
        // TEMPORARY diagnostic logging - remove once the DNL-02 Range investigation is closed.
        log.debug("downloadFile: file.sizeBytes()={}, ByteRangeParser.parse result present={}, value={}",
                file.sizeBytes(), range.isPresent(), range.orElse(null));

        if (range.isEmpty()) {
            StreamingResponseBody body = outputStream -> downloadService.streamTo(file.objectKey(), outputStream);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(file.sizeBytes())
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(body);
        }

        ByteRange byteRange = range.get();
        long contentLength = byteRange.length();
        StreamingResponseBody body = outputStream ->
                downloadService.streamRange(file.objectKey(), byteRange.start(), contentLength, outputStream);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .contentLength(contentLength)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE,
                        "bytes %d-%d/%d".formatted(byteRange.start(), byteRange.end(), file.sizeBytes()))
                .body(body);
    }

    private static MediaType resolveMediaType(String mimeType) {
        if (mimeType == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (RuntimeException e) {
            // mimeType is client-supplied at upload time (UPL-02) and only length-validated,
            // not format-validated - fall back rather than fail the whole download over it.
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
