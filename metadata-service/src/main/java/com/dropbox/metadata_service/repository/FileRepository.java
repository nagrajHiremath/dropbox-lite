package com.dropbox.metadata_service.repository;

import com.dropbox.metadata_service.domain.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface FileRepository extends JpaRepository<FileEntity, UUID>, JpaSpecificationExecutor<FileEntity> {

    Optional<FileEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
}
