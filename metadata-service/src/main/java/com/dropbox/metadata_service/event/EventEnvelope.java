package com.dropbox.metadata_service.event;

import java.time.Instant;
import java.util.UUID;

/**
 * EVT-01. Standard envelope for events this service consumes (and, once
 * OBX-02 builds this service's own publisher, produces) - see
 * TECHNICAL_DESIGN.md "33. Event Envelope". Mirrors upload-service's copy;
 * kept as this service's own rather than a shared library, per this
 * project's "no shared business-domain library" rule.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID aggregateId,
        UUID userId,
        T data
) {
}
