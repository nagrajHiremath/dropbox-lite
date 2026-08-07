package com.dropbox.metadata_service.repository;

import com.dropbox.metadata_service.domain.FileVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {

    Page<FileVersion> findByFileIdOrderByVersionNumberDesc(UUID fileId, Pageable pageable);

    Optional<FileVersion> findByIdAndFileId(UUID id, UUID fileId);

    Optional<FileVersion> findTopByFileIdOrderByVersionNumberDesc(UUID fileId);
}
