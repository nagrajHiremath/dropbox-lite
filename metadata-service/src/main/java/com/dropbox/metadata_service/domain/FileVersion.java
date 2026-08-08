package com.dropbox.metadata_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_versions",
        uniqueConstraints = @UniqueConstraint(name = "uq_file_versions_file_version", columnNames = {"file_id", "version_number"}),
        indexes = @Index(name = "idx_file_versions_file_version", columnList = "file_id, version_number"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column
    private String checksum;

    @Column
    private String etag;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Upload Service's upload_sessions.id, when this version was materialized from a
     * completed upload. Nullable + unique so a retried completion call can be detected
     * and replayed instead of creating a duplicate file/version (see UPL-05). Postgres
     * treats multiple NULLs as distinct, so versions created some other way (e.g. a
     * future Kafka-driven path, or version-restore) are unaffected.
     */
    @Column(name = "source_upload_id", unique = true)
    private UUID sourceUploadId;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
