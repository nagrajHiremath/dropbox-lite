package com.dropbox.upload_service.event;

import java.time.Instant;
import java.util.UUID;

/**
 * EVT-01. Standard envelope for every event this service publishes to Kafka
 * via its Outbox (see TECHNICAL_DESIGN.md "33. Event Envelope"). Serialized
 * as a whole into outbox_events.payload at write time; the publisher ships
 * that JSON verbatim, no reconstruction at publish time.
 *
 * Kept as this service's own copy rather than a shared library, per this
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
