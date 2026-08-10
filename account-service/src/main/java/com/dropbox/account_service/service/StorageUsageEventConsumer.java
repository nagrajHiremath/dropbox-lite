package com.dropbox.account_service.service;

import com.dropbox.account_service.domain.ProcessedEvent;
import com.dropbox.account_service.event.EventEnvelope;
import com.dropbox.account_service.repository.ProcessedEventRepository;
import com.dropbox.account_service.repository.StorageQuotaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Consumes file.lifecycle.v1 and keeps storage_quotas.used_bytes accurate -
 * TECHNICAL_DESIGN.md 9.2's "denormalized for fast quota checks", previously
 * unimplemented (the column only ever got set to 0 at registration). Only
 * two of the topic's event types change quota:
 *
 *   FILE_CREATED / FILE_VERSION_CREATED -> +data.sizeBytes (that version's size)
 *   FILE_PERMANENTLY_DELETED            -> -data.totalSizeBytes (every version's size - see
 *                                           async-worker's WRK-02, which deletes all version
 *                                           objects, not just the current one)
 *
 * FILE_TRASHED/FILE_RESTORED/FILE_SHARED/SHARE_REVOKED are deliberately
 * no-ops here: a trashed file still counts against quota until it's
 * permanently deleted.
 *
 * Reads payload fields via JsonNode rather than a shared typed record - this
 * consumer only needs two fields off a topic schema it doesn't own, the same
 * loosely-typed approach this topic's other consumers (async-worker's
 * PermanentDeletionConsumer) already use.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageUsageEventConsumer {

    private static final String CONSUMER_NAME = "account-service";

    private final ProcessedEventRepository processedEventRepository;
    private final StorageQuotaRepository storageQuotaRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "file.lifecycle.v1", groupId = CONSUMER_NAME)
    @Transactional
    public void onMessage(String message) {
        EventEnvelope<JsonNode> envelope;
        try {
            envelope = parse(message);
        } catch (Exception e) {
            log.error("Discarding unparseable file.lifecycle.v1 message: {}", e.getMessage());
            return;
        }

        long delta = deltaFor(envelope);
        if (delta == 0) {
            return;
        }

        MDC.put("eventId", envelope.eventId().toString());
        try {
            if (processedEventRepository.existsByEventIdAndConsumerName(envelope.eventId(), CONSUMER_NAME)) {
                log.debug("Skipping already-processed event {}", envelope.eventId());
                return;
            }

            int updated = storageQuotaRepository.adjustUsedBytes(envelope.userId(), delta);
            if (updated == 0) {
                log.warn("No storage_quotas row for user {} while processing event {}",
                        envelope.userId(), envelope.eventId());
            }
            processedEventRepository.save(new ProcessedEvent(envelope.eventId(), CONSUMER_NAME, Instant.now()));
            log.info("Applied storage usage delta {} for user {} ({})",
                    delta, envelope.userId(), envelope.eventType());
        } finally {
            MDC.remove("eventId");
        }
    }

    private long deltaFor(EventEnvelope<JsonNode> envelope) {
        JsonNode data = envelope.data();
        return switch (envelope.eventType()) {
            case "FILE_CREATED", "FILE_VERSION_CREATED" -> data.path("sizeBytes").asLong(0);
            case "FILE_PERMANENTLY_DELETED" -> -data.path("totalSizeBytes").asLong(0);
            default -> 0;
        };
    }

    private EventEnvelope<JsonNode> parse(String message) throws JsonProcessingException {
        JavaType type = objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, JsonNode.class);
        return objectMapper.readValue(message, type);
    }
}
