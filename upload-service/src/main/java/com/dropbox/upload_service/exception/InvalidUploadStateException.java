package com.dropbox.upload_service.exception;

public class InvalidUploadStateException extends RuntimeException {

    public InvalidUploadStateException(String message) {
        super(message);
    }
}
