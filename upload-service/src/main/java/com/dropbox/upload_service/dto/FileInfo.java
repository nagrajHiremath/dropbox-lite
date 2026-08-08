package com.dropbox.upload_service.dto;

/**
 * Subset of metadata-service's public FileResponse that Upload Service needs
 * to validate a target file for a new-version upload (VER-01). Deserialized
 * from that larger payload - unknown properties are ignored by default.
 */
public record FileInfo(
        String name,
        String mimeType,
        String status
) {
}
