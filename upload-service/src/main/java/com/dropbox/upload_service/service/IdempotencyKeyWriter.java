package com.dropbox.upload_service.service;

import com.dropbox.upload_service.domain.IdempotencyKey;
import com.dropbox.upload_service.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * All idempotency-key persistence runs in its own physical transaction
 * (REQUIRES_NEW), independent of whatever the caller is doing. UploadInitiationService
 * reserves a key before doing any side-effecting work, so a unique-constraint race
 * between two concurrent first-uses of the same key only rolls back the loser's
 * isolated reservation attempt here - never the caller's own state - and the loser
 * can safely re-read the winning row afterwards instead of operating on a
 * transaction Postgres has already aborted.
 *
 * Kept as a separate bean deliberately: REQUIRES_NEW only takes effect through the
 * Spring proxy, so these cannot be private/self-invoked methods on the caller.
 */
@Component
@RequiredArgsConstructor
class IdempotencyKeyWriter {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(IdempotencyKey record) {
        idempotencyKeyRepository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeSuccess(UUID userId, String operation, String idempotencyKey,
                                 UUID resourceId, int responseStatus, String responseBody) {
        idempotencyKeyRepository.findByUserIdAndOperationAndIdempotencyKey(userId, operation, idempotencyKey)
                .ifPresent(record -> {
                    record.setResourceId(resourceId);
                    record.setStatus("COMPLETED");
                    record.setResponseStatus(responseStatus);
                    record.setResponseBody(responseBody);
                    idempotencyKeyRepository.save(record);
                });
    }

    /**
     * Releases a reservation whose side-effecting work failed, so a retry with the
     * same key isn't permanently blocked by an attempt that never completed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID userId, String operation, String idempotencyKey) {
        idempotencyKeyRepository.findByUserIdAndOperationAndIdempotencyKey(userId, operation, idempotencyKey)
                .ifPresent(idempotencyKeyRepository::delete);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyKey> find(UUID userId, String operation, String idempotencyKey) {
        return idempotencyKeyRepository.findByUserIdAndOperationAndIdempotencyKey(userId, operation, idempotencyKey);
    }
}
