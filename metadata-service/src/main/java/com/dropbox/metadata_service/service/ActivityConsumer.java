package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.event.EventEnvelope;
import com.dropbox.metadata_service.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * WRK-01: records an activity-feed entry for every file.lifecycle.v1 event
 * (FILE_CREATED, FILE_VERSION_CREATED, FILE_TRASHED, FILE_RESTORED,
 * FILE_PERMANENTLY_DELETED, FILE_SHARED, SHARE_REVOKED - see OBX-02). Never
 * needs typed access to the event-specific data shape - it's stored verbatim
 * as activities.metadata - so this parses EventEnvelope<JsonNode> rather than
 * a per-eventType data class.
 *
 * Uses the default kafkaListenerContainerFactory bean (see KafkaConsumerConfig,
 * EVT-03) by not overriding containerFactory - inherits the same 2s/10s/30s
 * retry + DLT-to-file.lifecycle.v1.DLT behavior for free.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityConsumer {

    private static final String CONSUMER_NAME = "metadata-service-activity";

    private final ProcessedEventRepository processedEventRepository;
    private final ActivityWriter activityWriter;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "file.lifecycle.v1", groupId = "metadata-service-activity")
    public void onMessage(String message) {
        EventEnvelope<JsonNode> envelope;
        try {
            envelope = parse(message);
        } catch (Exception e) {
            log.error("Discarding unparseable file.lifecycle.v1 message: {}", e.getMessage());
            return;
        }

        if (processedEventRepository.existsByEventIdAndConsumerName(envelope.eventId(), CONSUMER_NAME)) {
            log.debug("Skipping already-recorded activity for event {}", envelope.eventId());
            return;
        }

        String metadataJson = envelope.data() != null ? envelope.data().toString() : null;
        activityWriter.recordActivity(envelope.eventId(), envelope.userId(), envelope.aggregateId(),
                envelope.eventType(), metadataJson);
    }

    private EventEnvelope<JsonNode> parse(String message) throws JsonProcessingException {
        JavaType type = objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, JsonNode.class);
        return objectMapper.readValue(message, type);
    }
}
