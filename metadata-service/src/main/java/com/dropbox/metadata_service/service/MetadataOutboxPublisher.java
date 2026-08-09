package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.OutboxEvent;
import com.dropbox.metadata_service.domain.OutboxEventStatus;
import com.dropbox.metadata_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OBX-02: polls PENDING outbox_events and ships them to file.lifecycle.v1 -
 * mirrors upload-service's OutboxPublisher (OBX-01) exactly. See
 * TECHNICAL_DESIGN.md 39 "Transactional Outbox".
 *
 * A send failure (including Kafka being unreachable) leaves the row PENDING
 * for the next tick to retry, up to MAX_RETRY_COUNT, after which the row is
 * marked FAILED rather than retried forever. A crash between a successful
 * Kafka send and marking PUBLISHED just republishes on the next tick -
 * downstream consumer idempotency (processed_events) is what makes duplicate
 * publication harmless, not this publisher.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataOutboxPublisher {

    private static final String TOPIC = "file.lifecycle.v1";
    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.publisher.interval-ms:2000}")
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        for (OutboxEvent event : pending) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
            event.setStatus(OutboxEventStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            outboxEventRepository.save(event);
        } catch (Exception e) {
            handleFailure(event, e);
        }
    }

    private void handleFailure(OutboxEvent event, Exception e) {
        int retryCount = event.getRetryCount() + 1;
        event.setRetryCount(retryCount);
        if (retryCount >= MAX_RETRY_COUNT) {
            event.setStatus(OutboxEventStatus.FAILED);
            log.error("Giving up publishing outbox event {} ({}) after {} attempts: {}",
                    event.getId(), event.getEventType(), retryCount, e.getMessage());
        } else {
            log.warn("Failed to publish outbox event {} ({}), attempt {}/{}: {}",
                    event.getId(), event.getEventType(), retryCount, MAX_RETRY_COUNT, e.getMessage());
        }
        outboxEventRepository.save(event);
    }
}
