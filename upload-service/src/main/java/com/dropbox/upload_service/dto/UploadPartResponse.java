package com.dropbox.upload_service.dto;

public record UploadPartResponse(
        int partNumber,
        String etag,
        long sizeBytes,
        String checksum,
        String status
) {
}
