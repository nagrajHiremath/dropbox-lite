package com.dropbox.download_service.service;

import com.dropbox.download_service.exception.RangeNotSatisfiableException;

import java.util.Optional;

/**
 * Parses a single-range "Range: bytes=..." header per RFC 7233: start-end,
 * open-ended start-, and suffix -length forms. Multi-range requests and
 * syntactically malformed headers are treated as absent (the caller falls
 * back to serving the full resource, per RFC 7233 section 3.1) rather than as
 * errors; only a syntactically valid but unsatisfiable range (e.g. starting
 * past the end of the resource) is an error.
 */
public final class ByteRangeParser {

    private static final String PREFIX = "bytes=";

    private ByteRangeParser() {
    }

    public record ByteRange(long start, long end) {
        public long length() {
            return end - start + 1;
        }
    }

    public static Optional<ByteRange> parse(String rangeHeader, long totalSize) {
        if (rangeHeader == null || !rangeHeader.startsWith(PREFIX)) {
            return Optional.empty();
        }

        String spec = rangeHeader.substring(PREFIX.length()).trim();
        if (spec.isEmpty() || spec.contains(",")) {
            return Optional.empty();
        }

        int dashIndex = spec.indexOf('-');
        if (dashIndex < 0) {
            return Optional.empty();
        }

        String startPart = spec.substring(0, dashIndex);
        String endPart = spec.substring(dashIndex + 1);

        try {
            if (startPart.isEmpty()) {
                if (endPart.isEmpty()) {
                    return Optional.empty();
                }
                long suffixLength = Long.parseLong(endPart);
                if (suffixLength <= 0 || totalSize <= 0) {
                    throw new RangeNotSatisfiableException(totalSize);
                }
                long start = Math.max(0, totalSize - suffixLength);
                return Optional.of(new ByteRange(start, totalSize - 1));
            }

            long start = Long.parseLong(startPart);
            long end = endPart.isEmpty() ? totalSize - 1 : Long.parseLong(endPart);

            if (start < 0 || start > end || start >= totalSize) {
                throw new RangeNotSatisfiableException(totalSize);
            }

            end = Math.min(end, totalSize - 1);
            return Optional.of(new ByteRange(start, end));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
