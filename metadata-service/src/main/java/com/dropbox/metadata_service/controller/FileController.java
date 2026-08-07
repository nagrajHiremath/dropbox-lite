package com.dropbox.metadata_service.controller;

import com.dropbox.metadata_service.domain.FileEntity;
import com.dropbox.metadata_service.dto.FileResponse;
import com.dropbox.metadata_service.dto.MoveFileRequest;
import com.dropbox.metadata_service.dto.PageResponse;
import com.dropbox.metadata_service.dto.UpdateFileRequest;
import com.dropbox.metadata_service.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @GetMapping("/{fileId}")
    public ResponseEntity<FileResponse> getFile(@AuthenticationPrincipal UUID ownerId,
                                                 @PathVariable UUID fileId) {
        FileEntity file = fileService.getFile(ownerId, fileId);
        return ResponseEntity.ok(FileResponse.from(file));
    }

    @GetMapping
    public ResponseEntity<PageResponse<FileResponse>> listFiles(@AuthenticationPrincipal UUID ownerId,
                                                                  @RequestParam(required = false) UUID folderId,
                                                                  @RequestParam(required = false) String view,
                                                                  @RequestParam(required = false) String type,
                                                                  @RequestParam(required = false) String status,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        Page<FileEntity> files = fileService.listFiles(ownerId, folderId, view, type, status, page, size);
        return ResponseEntity.ok(PageResponse.from(files, FileResponse::from));
    }

    @PatchMapping("/{fileId}")
    public ResponseEntity<FileResponse> renameFile(@AuthenticationPrincipal UUID ownerId,
                                                     @PathVariable UUID fileId,
                                                     @Valid @RequestBody UpdateFileRequest request) {
        FileEntity file = fileService.renameFile(ownerId, fileId, request);
        return ResponseEntity.ok(FileResponse.from(file));
    }

    @PostMapping("/{fileId}/move")
    public ResponseEntity<FileResponse> moveFile(@AuthenticationPrincipal UUID ownerId,
                                                   @PathVariable UUID fileId,
                                                   @RequestBody MoveFileRequest request) {
        FileEntity file = fileService.moveFile(ownerId, fileId, request);
        return ResponseEntity.ok(FileResponse.from(file));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> trashFile(@AuthenticationPrincipal UUID ownerId,
                                           @PathVariable UUID fileId) {
        fileService.trashFile(ownerId, fileId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{fileId}/restore")
    public ResponseEntity<FileResponse> restoreFile(@AuthenticationPrincipal UUID ownerId,
                                                       @PathVariable UUID fileId) {
        FileEntity file = fileService.restoreFile(ownerId, fileId);
        return ResponseEntity.ok(FileResponse.from(file));
    }

    @DeleteMapping("/{fileId}/permanent")
    public ResponseEntity<Void> permanentlyDeleteFile(@AuthenticationPrincipal UUID ownerId,
                                                        @PathVariable UUID fileId) {
        fileService.permanentlyDeleteFile(ownerId, fileId);
        return ResponseEntity.noContent().build();
    }
}
