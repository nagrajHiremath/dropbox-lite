package com.dropbox.metadata_service.repository;

import com.dropbox.metadata_service.domain.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {
}
