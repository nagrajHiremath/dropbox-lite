package com.dropbox.upload_service.repository;

import com.dropbox.upload_service.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByUserIdAndOperationAndIdempotencyKey(UUID userId, String operation, String idempotencyKey);
}
