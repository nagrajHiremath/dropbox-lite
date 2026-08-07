package com.dropbox.upload_service.repository;

import com.dropbox.upload_service.domain.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {
}
