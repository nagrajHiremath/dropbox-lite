package com.dropbox.upload_service.service;

import com.dropbox.upload_service.domain.OutboxEvent;
import com.dropbox.upload_service.domain.OutboxEventStatus;
import com.dropbox.upload_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OBX-01: polls PENDING outbox_events and ships them to Kafka - see
 * TECHNICAL_DESIGN.md 39 "Transactional Outbox". Runs on a schedule rather
 * than reacting to the write itself, since the write already committed
 * durably; this just needs to eventually notice it.
 *
 * A send failure (including Kafka being unreachable) leaves the row PENDING
 * for the next tick to retry - "Kafka unavailable does not lose event" - up
 * to MAX_RETRY_COUNT, after which the row is marked FAILED rather than
 * retried forever. A crash between a successful Kafka send and marking
 * PUBLISHED just republishes on the next tick - "Duplicate publication
 * tolerated" is a Kafka consumer-idempotency concern (processed_events), not
 * something this publisher needs to prevent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final String TOPIC = "storage.lifecycle.v1";
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
            log.debug("Published outbox event {} ({}) for aggregate {} to {}",
                    event.getId(), event.getEventType(), event.getAggregateId(), TOPIC);
        } catch (Exception e) {
            handleFailure(event, e);
        }
    }

    private void handleFailure(OutboxEvent event, Exception e) {
        int retryCount = event.getRetryCount() + 1;
        event.setRetryCount(retryCount);
        if (retryCount >= MAX_RETRY_COUNT) {
            event.setStatus(OutboxEventStatus.FAILED);
            log.error("Giving up publishing outbox event {} ({}) for aggregate {} after {} attempts: {}",
                    event.getId(), event.getEventType(), event.getAggregateId(), retryCount, e.getMessage());
        } else {
            log.warn("Failed to publish outbox event {} ({}) for aggregate {}, attempt {}/{}: {}",
                    event.getId(), event.getEventType(), event.getAggregateId(), retryCount, MAX_RETRY_COUNT, e.getMessage());
        }
        outboxEventRepository.save(event);
    }
}
