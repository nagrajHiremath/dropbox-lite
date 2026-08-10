package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.FileEntity;
import com.dropbox.metadata_service.domain.FileStatus;
import com.dropbox.metadata_service.domain.FileVersion;
import com.dropbox.metadata_service.domain.ShareLink;
import com.dropbox.metadata_service.domain.ShareStatus;
import com.dropbox.metadata_service.dto.FileContentInfoResponse;
import com.dropbox.metadata_service.exception.ForbiddenException;
import com.dropbox.metadata_service.repository.FileRepository;
import com.dropbox.metadata_service.repository.FileVersionRepository;
import com.dropbox.metadata_service.repository.ShareLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused on the VIEW-vs-DOWNLOAD authorization gap in resolvePublicShareContent -
 * token hashing, ACTIVE-status and expiry validation (resolveActiveShare) are
 * exercised incidentally by every case here but aren't the point of this test.
 */
@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private ShareLinkRepository shareLinkRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private FileVersionRepository fileVersionRepository;
    @Mock
    private OutboxEventWriter outboxEventWriter;
    @Mock
    private RedisCacheService cacheService;

    private ShareService service() {
        return new ShareService(shareLinkRepository, fileRepository, fileVersionRepository, outboxEventWriter, cacheService);
    }

    private FileEntity activeFile(UUID fileId, UUID currentVersionId) {
        return FileEntity.builder()
                .id(fileId)
                .ownerId(UUID.randomUUID())
                .name("report.pdf")
                .mimeType("application/pdf")
                .currentVersionId(currentVersionId)
                .status(FileStatus.ACTIVE)
                .build();
    }

    private ShareLink activeShare(UUID fileId, String permission) {
        return ShareLink.builder()
                .id(UUID.randomUUID())
                .fileId(fileId)
                .tokenHash("irrelevant-in-this-test")
                .permission(permission)
                .status(ShareStatus.ACTIVE)
                .expiresAt(null)
                .createdBy(UUID.randomUUID())
                .build();
    }

    private void stubActiveShare(ShareLink share, FileEntity file) {
        when(cacheService.get(any(), eq(ShareLink.class))).thenReturn(Optional.empty());
        when(shareLinkRepository.findByTokenHash(any())).thenReturn(Optional.of(share));
        when(fileRepository.findById(file.getId())).thenReturn(Optional.of(file));
    }

    @Test
    void viewOnlyShareIsForbiddenFromResolvingContent() {
        UUID fileId = UUID.randomUUID();
        FileEntity file = activeFile(fileId, UUID.randomUUID());
        ShareLink share = activeShare(fileId, "VIEW");
        stubActiveShare(share, file);

        assertThatThrownBy(() -> service().resolvePublicShareContent("raw-token"))
                .isInstanceOf(ForbiddenException.class);

        verify(fileVersionRepository, never()).findById(any());
    }

    @Test
    void downloadShareResolvesContent() {
        UUID fileId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        FileEntity file = activeFile(fileId, versionId);
        ShareLink share = activeShare(fileId, "DOWNLOAD");
        stubActiveShare(share, file);

        FileVersion version = FileVersion.builder()
                .id(versionId)
                .fileId(fileId)
                .versionNumber(1)
                .objectKey("dropbox-files/owner/file/v1")
                .sizeBytes(2048L)
                .createdBy(file.getOwnerId())
                .build();
        when(fileVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

        FileContentInfoResponse response = service().resolvePublicShareContent("raw-token");

        assertThat(response.fileName()).isEqualTo("report.pdf");
        assertThat(response.mimeType()).isEqualTo("application/pdf");
        assertThat(response.objectKey()).isEqualTo("dropbox-files/owner/file/v1");
        assertThat(response.sizeBytes()).isEqualTo(2048L);
    }

    @Test
    void viewOnlyShareStillResolvesMetadata() {
        UUID fileId = UUID.randomUUID();
        FileEntity file = activeFile(fileId, UUID.randomUUID());
        ShareLink share = activeShare(fileId, "VIEW");
        stubActiveShare(share, file);

        ShareService.PublicShareView view = service().resolvePublicShare("raw-token");

        assertThat(view.permission()).isEqualTo("VIEW");
        assertThat(view.file().getId()).isEqualTo(fileId);
    }
}
