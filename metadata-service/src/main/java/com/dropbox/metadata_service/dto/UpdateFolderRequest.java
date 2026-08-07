package com.dropbox.metadata_service.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * PATCH semantics: a null field means "leave unchanged". There is no distinct
 * "move to root" signal in this MVP; use a dedicated move/root convention if that
 * becomes necessary.
 */
public record UpdateFolderRequest(
        @Size(max = 255) String name,
        UUID parentId
) {
}
