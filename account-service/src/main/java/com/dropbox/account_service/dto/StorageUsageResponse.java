package com.dropbox.account_service.dto;

public record StorageUsageResponse(
        long usedBytes,
        long maxBytes
) {
}
