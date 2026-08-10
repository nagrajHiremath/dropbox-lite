package com.dropbox.metadata_service.repository;

import com.dropbox.metadata_service.domain.FileVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {

    Page<FileVersion> findByFileIdOrderByVersionNumberDesc(UUID fileId, Pageable pageable);

    List<FileVersion> findByFileId(UUID fileId);

    Optional<FileVersion> findByIdAndFileId(UUID id, UUID fileId);

    Optional<FileVersion> findTopByFileIdOrderByVersionNumberDesc(UUID fileId);

    Optional<FileVersion> findBySourceUploadId(UUID sourceUploadId);

    /** Every version's object is deleted independently on permanent delete
     * (see async-worker's PermanentDeletionConsumer/WRK-02), so this is the
     * total quota to free - not just the current version's size. */
    @Query("SELECT COALESCE(SUM(v.sizeBytes), 0) FROM FileVersion v WHERE v.fileId = :fileId")
    long sumSizeBytesByFileId(@Param("fileId") UUID fileId);
}
