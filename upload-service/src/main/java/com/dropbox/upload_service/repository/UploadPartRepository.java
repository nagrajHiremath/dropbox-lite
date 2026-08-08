package com.dropbox.upload_service.repository;

import com.dropbox.upload_service.domain.UploadPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadPartRepository extends JpaRepository<UploadPart, UUID> {

    Optional<UploadPart> findByUploadSessionIdAndPartNumber(UUID uploadSessionId, int partNumber);

    @Query("select p.partNumber from UploadPart p where p.uploadSessionId = :uploadSessionId order by p.partNumber asc")
    List<Integer> findPartNumbersByUploadSessionId(@Param("uploadSessionId") UUID uploadSessionId);
}
