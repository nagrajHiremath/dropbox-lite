package com.dropbox.upload_service.exception;

/**
 * An external dependency this request needed (MinIO, Metadata Service) could
 * not be reached or failed unexpectedly. Mapped to 502 - the request should
 * not have left any durable state behind.
 */
public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String message) {
        super(message);
    }

    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
