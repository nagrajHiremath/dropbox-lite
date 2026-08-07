package com.dropbox.metadata_service.repository;

import com.dropbox.metadata_service.domain.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID>, JpaSpecificationExecutor<Folder> {

    Optional<Folder> findByIdAndOwnerId(UUID id, UUID ownerId);
}
