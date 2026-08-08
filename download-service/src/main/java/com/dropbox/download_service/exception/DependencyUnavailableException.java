package com.dropbox.download_service.exception;

/**
 * An external dependency this request needed (MinIO, Metadata Service) could
 * not be reached or failed unexpectedly. Mapped to 502.
 */
public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String message) {
        super(message);
    }

    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
