package com.dropbox.metadata_service.controller;

import com.dropbox.metadata_service.domain.Folder;
import com.dropbox.metadata_service.dto.CreateFolderRequest;
import com.dropbox.metadata_service.dto.FolderResponse;
import com.dropbox.metadata_service.dto.PageResponse;
import com.dropbox.metadata_service.dto.UpdateFolderRequest;
import com.dropbox.metadata_service.service.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(@AuthenticationPrincipal UUID ownerId,
                                                         @Valid @RequestBody CreateFolderRequest request) {
        Folder folder = folderService.createFolder(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(FolderResponse.from(folder));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FolderResponse> getFolder(@AuthenticationPrincipal UUID ownerId,
                                                      @PathVariable UUID id) {
        Folder folder = folderService.getFolder(ownerId, id);
        return ResponseEntity.ok(FolderResponse.from(folder));
    }

    /**
     * Omitting parentId lists root-level folders. Mirrors GET /files?folderId=,
     * since the documented API has no other way to reach the root of the tree.
     *
     * status=trashed mirrors GET /files?status=trashed: an account-wide trash
     * view that ignores parentId entirely, rather than a per-folder listing.
     */
    @GetMapping
    public ResponseEntity<PageResponse<FolderResponse>> listByParent(@AuthenticationPrincipal UUID ownerId,
                                                                       @RequestParam(required = false) UUID parentId,
                                                                       @RequestParam(required = false) String status,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        if ("trashed".equalsIgnoreCase(status)) {
            Page<Folder> trashed = folderService.listTrashed(ownerId, page, size);
            return ResponseEntity.ok(PageResponse.from(trashed, FolderResponse::from));
        }

        if (parentId != null) {
            folderService.getFolder(ownerId, parentId);
        }
        Page<Folder> folders = folderService.listChildren(ownerId, parentId, page, size);
        return ResponseEntity.ok(PageResponse.from(folders, FolderResponse::from));
    }

    @GetMapping("/{id}/children")
    public ResponseEntity<PageResponse<FolderResponse>> listChildren(@AuthenticationPrincipal UUID ownerId,
                                                                       @PathVariable UUID id,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        folderService.getFolder(ownerId, id);
        Page<Folder> children = folderService.listChildren(ownerId, id, page, size);
        return ResponseEntity.ok(PageResponse.from(children, FolderResponse::from));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FolderResponse> updateFolder(@AuthenticationPrincipal UUID ownerId,
                                                         @PathVariable UUID id,
                                                         @Valid @RequestBody UpdateFolderRequest request) {
        Folder folder = folderService.updateFolder(ownerId, id, request);
        return ResponseEntity.ok(FolderResponse.from(folder));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> trashFolder(@AuthenticationPrincipal UUID ownerId,
                                             @PathVariable UUID id) {
        folderService.trashFolder(ownerId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<FolderResponse> restoreFolder(@AuthenticationPrincipal UUID ownerId,
                                                          @PathVariable UUID id) {
        Folder folder = folderService.restoreFolder(ownerId, id);
        return ResponseEntity.ok(FolderResponse.from(folder));
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanentlyDeleteFolder(@AuthenticationPrincipal UUID ownerId,
                                                          @PathVariable UUID id) {
        folderService.permanentlyDeleteFolder(ownerId, id);
        return ResponseEntity.noContent().build();
    }
}
