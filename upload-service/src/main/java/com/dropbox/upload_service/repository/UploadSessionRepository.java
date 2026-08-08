package com.dropbox.upload_service.repository;

import com.dropbox.upload_service.domain.UploadSession;
import com.dropbox.upload_service.domain.UploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {

    Optional<UploadSession> findByIdAndUserId(UUID id, UUID userId);

    List<UploadSession> findByExpiresAtBeforeAndStatusIn(Instant expiresAt, Collection<UploadStatus> statuses);

    /**
     * Atomically flips a session to EXPIRED only if its status is still one of
     * unfinishedStatuses at update time. The WHERE clause is the race guard (see
     * ExpiredUploadCleanupService): concurrent sweeps or a genuine completion
     * racing the sweep converge safely because Postgres re-checks status under
     * the row lock, not against a possibly-stale in-memory read. Returns 0 if
     * another writer already moved the session past that set.
     */
    @Modifying
    @Query("update UploadSession s set s.status = com.dropbox.upload_service.domain.UploadStatus.EXPIRED, " +
            "s.updatedAt = :now where s.id = :id and s.status in :unfinishedStatuses")
    int markExpiredIfStillUnfinished(@Param("id") UUID id, @Param("now") Instant now,
                                      @Param("unfinishedStatuses") Collection<UploadStatus> unfinishedStatuses);
}
