package com.dropbox.metadata_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka consumer idempotency (see TECHNICAL_DESIGN.md 16 / "38. Kafka
 * Consumer Idempotency"). Composite PK (event_id, consumer_name) matches the
 * documented schema literally, unlike the rest of this codebase's usual
 * single-generated-UUID-PK-plus-unique-constraint convention.
 */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEvent.ProcessedEventId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Id
    @Column(name = "consumer_name")
    private String consumerName;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class ProcessedEventId implements Serializable {
        private UUID eventId;
        private String consumerName;
    }
}
