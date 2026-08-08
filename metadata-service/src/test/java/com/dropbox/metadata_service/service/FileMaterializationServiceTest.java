package com.dropbox.metadata_service.service;

import com.dropbox.metadata_service.domain.FileVersion;
import com.dropbox.metadata_service.domain.Folder;
import com.dropbox.metadata_service.domain.FolderStatus;
import com.dropbox.metadata_service.dto.MaterializeFileRequest;
import com.dropbox.metadata_service.dto.MaterializeFileResponse;
import com.dropbox.metadata_service.exception.InvalidRequestException;
import com.dropbox.metadata_service.exception.ResourceNotFoundException;
import com.dropbox.metadata_service.repository.FileRepository;
import com.dropbox.metadata_service.repository.FileVersionRepository;
import com.dropbox.metadata_service.repository.FolderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileMaterializationServiceTest {

    @Mock
    private FileVersionRepository fileVersionRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private FolderRepository folderRepository;
    @Mock
    private FileVersionMaterializer materializer;
    @Mock
    private RedisLockService lockService;

    private FileMaterializationService service() {
        return new FileMaterializationService(
                fileVersionRepository, fileRepository, folderRepository, materializer, lockService);
    }

    private MaterializeFileRequest request(UUID sourceUploadId, UUID folderId) {
        return new MaterializeFileRequest(sourceUploadId, folderId, "movie.mp4", "video/mp4",
                "dropbox-files/owner/upload/data", 1_500L, null, null);
    }

    @Test
    void replaysExistingMaterializationForSameSourceUploadId() {
        UUID ownerId = UUID.randomUUID();
        UUID sourceUploadId = UUID.randomUUID();
        FileVersion existing = FileVersion.builder()
                .id(UUID.randomUUID())
                .fileId(UUID.randomUUID())
                .versionNumber(1)
                .sourceUploadId(sourceUploadId)
                .build();
        when(fileVersionRepository.findBySourceUploadId(sourceUploadId)).thenReturn(Optional.of(existing));

        MaterializeFileResponse response = service().materialize(ownerId, request(sourceUploadId, null));

        assertThat(response.fileId()).isEqualTo(existing.getFileId());
        assertThat(response.versionId()).isEqualTo(existing.getId());
        assertThat(response.versionNumber()).isEqualTo(1);
        verify(materializer, never()).createFileAndVersion(any(), any());
    }

    @Test
    void createsFileAndVersionWhenNoneExists() {
        UUID ownerId = UUID.randomUUID();
        UUID sourceUploadId = UUID.randomUUID();
        MaterializeFileRequest request = request(sourceUploadId, null);
        FileVersion created = FileVersion.builder()
                .id(UUID.randomUUID())
                .fileId(UUID.randomUUID())
                .versionNumber(1)
                .sourceUploadId(sourceUploadId)
                .build();

        when(fileVersionRepository.findBySourceUploadId(sourceUploadId)).thenReturn(Optional.empty());
        when(materializer.createFileAndVersion(ownerId, request)).thenReturn(created);

        MaterializeFileResponse response = service().materialize(ownerId, request);

        assertThat(response.fileId()).isEqualTo(created.getFileId());
        assertThat(response.versionId()).isEqualTo(created.getId());
        assertThat(response.versionNumber()).isEqualTo(1);
        verify(materializer).createFileAndVersion(ownerId, request);
    }

    @Test
    void rejectsUnownedOrMissingFolder() {
        UUID ownerId = UUID.randomUUID();
        UUID sourceUploadId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        when(fileVersionRepository.findBySourceUploadId(sourceUploadId)).thenReturn(Optional.empty());
        when(folderRepository.findByIdAndOwnerId(folderId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().materialize(ownerId, request(sourceUploadId, folderId)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(materializer, never()).createFileAndVersion(any(), any());
    }

    @Test
    void rejectsInactiveFolder() {
        UUID ownerId = UUID.randomUUID();
        UUID sourceUploadId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        Folder folder = Folder.builder().id(folderId).ownerId(ownerId).status(FolderStatus.TRASHED).build();
        when(fileVersionRepository.findBySourceUploadId(sourceUploadId)).thenReturn(Optional.empty());
        when(folderRepository.findByIdAndOwnerId(folderId, ownerId)).thenReturn(Optional.of(folder));

        assertThatThrownBy(() -> service().materialize(ownerId, request(sourceUploadId, folderId)))
                .isInstanceOf(InvalidRequestException.class);

        verify(materializer, never()).createFileAndVersion(any(), any());
    }

    @Test
    void raceLossReloadsWinnerInsteadOfDuplicating() {
        UUID ownerId = UUID.randomUUID();
        UUID sourceUploadId = UUID.randomUUID();
        MaterializeFileRequest request = request(sourceUploadId, null);
        FileVersion winner = FileVersion.builder()
                .id(UUID.randomUUID())
                .fileId(UUID.randomUUID())
                .versionNumber(1)
                .sourceUploadId(sourceUploadId)
                .build();

        when(fileVersionRepository.findBySourceUploadId(sourceUploadId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        // The atomic create (FileEntity + FileVersion together) rolled back in full -
        // there is nothing left over to assert about here, that's the whole point.
        when(materializer.createFileAndVersion(ownerId, request))
                .thenThrow(new DataIntegrityViolationException("duplicate sourceUploadId"));

        MaterializeFileResponse response = service().materialize(ownerId, request);

        assertThat(response.fileId()).isEqualTo(winner.getFileId());
        assertThat(response.versionId()).isEqualTo(winner.getId());
    }
}
