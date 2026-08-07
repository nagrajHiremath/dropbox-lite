package com.dropbox.metadata_service.repository;

import com.dropbox.metadata_service.domain.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

    List<ShareLink> findByFileIdOrderByCreatedAtDesc(UUID fileId);

    Optional<ShareLink> findByTokenHash(String tokenHash);
}
