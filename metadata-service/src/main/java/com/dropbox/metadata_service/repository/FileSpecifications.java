package com.dropbox.metadata_service.repository;

import com.dropbox.metadata_service.domain.FileEntity;
import com.dropbox.metadata_service.domain.FileStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class FileSpecifications {

    private FileSpecifications() {
    }

    public static Specification<FileEntity> ownedBy(UUID ownerId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);
    }

    public static Specification<FileEntity> inFolder(UUID folderId) {
        return (root, query, cb) -> folderId == null
                ? cb.isNull(root.get("folderId"))
                : cb.equal(root.get("folderId"), folderId);
    }

    public static Specification<FileEntity> hasStatus(FileStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<FileEntity> mimeTypeStartsWith(String prefix) {
        return (root, query, cb) -> cb.like(root.get("mimeType"), prefix + "%");
    }

    public static Specification<FileEntity> hasName(String name) {
        return (root, query, cb) -> cb.equal(root.get("name"), name);
    }

    public static Specification<FileEntity> idNot(UUID id) {
        return (root, query, cb) -> cb.notEqual(root.get("id"), id);
    }
}
