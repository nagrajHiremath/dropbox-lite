package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.Activity;
import com.dropbox.metadata_service.domain.ProcessedEvent;
import com.dropbox.metadata_service.repository.ActivityRepository;
import com.dropbox.metadata_service.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * WRK-01: records one activity-feed entry and the corresponding
 * processed_events row in a single transaction. Safe to do as one step here
 * (unlike EVT-02's materialize()) - a plain insert has no conflict-and-reload
 * race to worry about poisoning.
 */
@Component
@RequiredArgsConstructor
class ActivityWriter {

    private static final String CONSUMER_NAME = "metadata-service-activity";

    private final ActivityRepository activityRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void recordActivity(UUID eventId, UUID userId, UUID fileId, String action, String metadataJson) {
        Activity activity = Activity.builder()
                .userId(userId)
                .fileId(fileId)
                .action(action)
                .metadata(metadataJson)
                .build();
        activityRepository.save(activity);

        processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER_NAME, Instant.now()));
    }
}
