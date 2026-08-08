package com.dropbox.download_service.web;

import com.dropbox.download_service.service.ByteRangeParser;
import com.dropbox.download_service.service.ByteRangeParser.ByteRange;
import com.dropbox.download_service.service.DownloadService;
import com.dropbox.download_service.service.DownloadService.ResolvedFile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Shared full/Range streaming response assembly, used by both
 * FileDownloadController (authenticated) and PublicShareDownloadController
 * (SHR-02, anonymous) - the response-building logic is identical once a
 * ResolvedFile has been authorized, only how that authorization happens
 * differs between the two callers.
 */
@Component
@RequiredArgsConstructor
public class DownloadResponseBuilder {

    private final DownloadService downloadService;

    public ResponseEntity<StreamingResponseBody> build(ResolvedFile file, String rangeHeader) {
        String contentDisposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString();
        MediaType mediaType = resolveMediaType(file.mimeType());

        Optional<ByteRange> range = ByteRangeParser.parse(rangeHeader, file.sizeBytes());

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
