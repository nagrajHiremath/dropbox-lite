package com.dropbox.metadata_service.exception;

public class NameConflictException extends RuntimeException {

    public NameConflictException(String message) {
        super(message);
    }
}
