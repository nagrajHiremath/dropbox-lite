package com.dropbox.async_worker.scheduler;

import com.dropbox.async_worker.client.UploadServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UPL-08: periodically triggers Upload Service's expired-session sweep (see
 * TECHNICAL_DESIGN.md 6.6 - "Expired upload cleanup" is a Worker
 * responsibility). One worker instance is enough for the MVP; the sweep
 * itself is safe under multiple concurrent callers since Upload Service
 * claims each session via a conditional UPDATE (see
 * ExpiredUploadCleanupService), so overlapping runs from a future
 * multi-instance deployment are not a correctness concern.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredUploadSweepScheduler {

    private final UploadServiceClient uploadServiceClient;

    @Scheduled(fixedDelayString = "${upload.expire-sweep.interval-ms:300000}")
    public void sweep() {
        try {
            int expiredCount = uploadServiceClient.triggerExpireSweep();
            if (expiredCount > 0) {
                log.info("Expired upload sweep marked {} session(s) EXPIRED", expiredCount);
            }
        } catch (Exception e) {
            // failures are retried on the next scheduled run; nothing durable is lost.
            log.warn("Expired upload sweep failed: {}", e.getMessage());
        }
    }
}
