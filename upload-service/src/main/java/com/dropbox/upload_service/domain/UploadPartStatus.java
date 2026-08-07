package com.dropbox.upload_service.domain;

/**
 * Reflects the per-part flow from the design doc: stream part to MinIO -> receive
 * ETag -> persist upload_parts. A row only exists once an attempt has concluded.
 */
public enum UploadPartStatus {
    UPLOADED,
    FAILED
}
