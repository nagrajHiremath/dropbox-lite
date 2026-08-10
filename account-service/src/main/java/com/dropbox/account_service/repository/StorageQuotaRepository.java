package com.dropbox.account_service.repository;

import com.dropbox.account_service.domain.StorageQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface StorageQuotaRepository extends JpaRepository<StorageQuota, UUID> {

    /** Atomic SQL increment/decrement, not read-modify-write: Kafka's
     * partition key for file.lifecycle.v1 is fileId (TECHNICAL_DESIGN.md 37),
     * not userId, so two events for the same user's different files can be
     * processed concurrently - a load-then-save here would lose updates
     * under that interleaving. GREATEST(0, ...) is a defensive floor against
     * out-of-order delivery (at-least-once, no cross-partition ordering
     * guarantee) ever driving used_bytes negative. Returns the number of
     * rows updated - 0 means the caller raced ahead of RegistrationService,
     * which should never happen since quota rows are created atomically with
     * the user at registration.
     */
    @Modifying
    @Query(value = "UPDATE storage_quotas SET used_bytes = GREATEST(0, used_bytes + :deltaBytes), "
            + "updated_at = now() WHERE user_id = :userId", nativeQuery = true)
    int adjustUsedBytes(@Param("userId") UUID userId, @Param("deltaBytes") long deltaBytes);
}
