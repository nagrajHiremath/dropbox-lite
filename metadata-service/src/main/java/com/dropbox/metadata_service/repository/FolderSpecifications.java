package com.dropbox.metadata_service.repository;

import com.dropbox.metadata_service.domain.Folder;
import com.dropbox.metadata_service.domain.FolderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class FolderSpecifications {

    private FolderSpecifications() {
    }

    public static Specification<Folder> ownedBy(UUID ownerId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);
    }

    public static Specification<Folder> hasParent(UUID parentId) {
        return (root, query, cb) -> parentId == null
                ? cb.isNull(root.get("parentId"))
                : cb.equal(root.get("parentId"), parentId);
    }

    public static Specification<Folder> hasStatus(FolderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Folder> hasName(String name) {
        return (root, query, cb) -> cb.equal(root.get("name"), name);
    }

    public static Specification<Folder> idNot(UUID id) {
        return (root, query, cb) -> cb.notEqual(root.get("id"), id);
    }
}
