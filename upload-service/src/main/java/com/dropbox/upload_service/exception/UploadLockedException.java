package com.dropbox.upload_service.exception;

public class UploadLockedException extends RuntimeException {

    public UploadLockedException(String message) {
        super(message);
    }
}
