package com.dropbox.account_service.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard envelope for events this service consumes - see
 * TECHNICAL_DESIGN.md "33. Event Envelope". Mirrors metadata-service's/
 * upload-service's own copy; kept as this service's own rather than a shared
 * library, per this project's "no shared business-domain library" rule.
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
