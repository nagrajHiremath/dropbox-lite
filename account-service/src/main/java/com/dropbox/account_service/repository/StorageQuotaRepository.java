package com.dropbox.account_service.repository;

import com.dropbox.account_service.domain.StorageQuota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StorageQuotaRepository extends JpaRepository<StorageQuota, UUID> {
}
