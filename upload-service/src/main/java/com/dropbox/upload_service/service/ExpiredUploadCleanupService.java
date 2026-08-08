package com.dropbox.upload_service.service;

import com.dropbox.upload_service.domain.UploadSession;
import com.dropbox.upload_service.domain.UploadStatus;
import com.dropbox.upload_service.repository.UploadSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UPL-08: identifies sessions past expires_at that never reached a composed
 * state (INITIATED/UPLOADING - abandoned before any MinIO compose was
 * attempted) and marks them EXPIRED, discarding their temp part objects via
 * the same UploadTempPartsCleaner used by abort/completion.
 *
 * COMPLETING/STORAGE_COMPLETED are deliberately excluded even though they are
 * "unfinished": STORAGE_COMPLETED means compose already succeeded and the
 * session is resumable via UploadCompletionService (materialization-only) -
 * expiring it here would abandon already-durable storage work instead of
 * letting completion finish it.
 *
 * Race safety ("safe when multiple workers exist" / "already-completed
 * uploads untouched"): each session is claimed via a single conditional
 * UPDATE (markExpiredIfStillUnfinished) that only succeeds if the status is
 * still unfinished at write time. A losing writer (session already completed
 * or already expired by another run) simply skips it - no distributed lock
 * needed, since the SQL WHERE clause under the row lock is the source of
 * truth. Temp part cleanup only runs after winning that claim, so a session's
 * objects are never deleted based on a stale read.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpiredUploadCleanupService {

    private static final List<UploadStatus> UNFINISHED_STATUSES = List.of(UploadStatus.INITIATED, UploadStatus.UPLOADING);

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadTempPartsCleaner tempPartsCleaner;

    public int cleanupExpired() {
        Instant now = Instant.now();
        List<UploadSession> candidates = uploadSessionRepository.findByExpiresAtBeforeAndStatusIn(now, UNFINISHED_STATUSES);

        int expiredCount = 0;
        for (UploadSession candidate : candidates) {
            try {
                if (expireOne(candidate.getId(), now)) {
                    expiredCount++;
                }
            } catch (Exception e) {
                // one session's failure must not abort the rest of the sweep; the
                // next scheduled run will retry it since it is still unfinished/expired.
                log.warn("Failed to expire upload session {}: {}", candidate.getId(), e.getMessage());
            }
        }
        return expiredCount;
    }

    private boolean expireOne(UUID sessionId, Instant now) {
        int updated = uploadSessionRepository.markExpiredIfStillUnfinished(sessionId, now, UNFINISHED_STATUSES);
        if (updated == 0) {
            return false;
        }
        uploadSessionRepository.findById(sessionId).ifPresent(tempPartsCleaner::cleanup);
        return true;
    }
}
