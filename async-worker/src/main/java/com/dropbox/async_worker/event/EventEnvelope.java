package com.dropbox.async_worker.event;

import java.time.Instant;
import java.util.UUID;

/**
 * EVT-01 envelope, async-worker's own copy - no shared library, matching
 * every other cross-service DTO convention in this project. This consumer
 * only ever needs envelope-level fields (eventType/aggregateId), never typed
 * access to data.
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
