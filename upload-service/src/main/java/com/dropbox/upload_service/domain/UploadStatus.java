package com.dropbox.upload_service.domain;

public enum UploadStatus {
    INITIATED,
    UPLOADING,
    COMPLETING,
    STORAGE_COMPLETED,
    COMPLETED,
    FAILED,
    ABORTED,
    EXPIRED
}
