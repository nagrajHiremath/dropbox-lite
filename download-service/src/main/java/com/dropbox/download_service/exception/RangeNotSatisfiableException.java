package com.dropbox.download_service.exception;

/**
 * A syntactically valid Range header could not be satisfied against the
 * resource's actual size (e.g. start beyond the end of the file). Mapped to
 * 416, with a Content-Range header of "bytes {asterisk}/{totalSize}" per RFC 7233 section 4.4.
 */
public class RangeNotSatisfiableException extends RuntimeException {

    private final long totalSize;

    public RangeNotSatisfiableException(long totalSize) {
        super("Requested range is not satisfiable for a resource of size " + totalSize);
        this.totalSize = totalSize;
    }

    public long getTotalSize() {
        return totalSize;
    }
}
