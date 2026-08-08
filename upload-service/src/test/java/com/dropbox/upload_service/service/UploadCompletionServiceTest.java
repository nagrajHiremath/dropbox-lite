package com.dropbox.upload_service.service;

import com.dropbox.upload_service.client.MetadataServiceClient;
import com.dropbox.upload_service.domain.UploadSession;
import com.dropbox.upload_service.domain.UploadStatus;
import com.dropbox.upload_service.domain.UploadType;
import com.dropbox.upload_service.dto.CompleteUploadResponse;
import com.dropbox.upload_service.dto.MaterializeFileRequest;
import com.dropbox.upload_service.dto.MaterializeFileResponse;
import com.dropbox.upload_service.exception.InvalidRequestException;
import com.dropbox.upload_service.exception.InvalidUploadStateException;
import com.dropbox.upload_service.exception.ResourceNotFoundException;
import com.dropbox.upload_service.repository.UploadPartRepository;
import com.dropbox.upload_service.repository.UploadSessionRepository;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadCompletionServiceTest {

    @Mock
    private UploadSessionRepository uploadSessionRepository;
    @Mock
    private UploadPartRepository uploadPartRepository;
    @Mock
    private MetadataServiceClient metadataServiceClient;
    @Mock
    private MinioClient minioClient;

    private UploadCompletionService service;

    @BeforeEach
    void setUp() {
        service = new UploadCompletionService(
                uploadSessionRepository, uploadPartRepository, metadataServiceClient, minioClient);
        ReflectionTestUtils.setField(service, "bucket", "dropbox-files");
    }

    private UploadSession session(UUID id, UUID ownerId, UploadStatus status, int totalParts, long chunkSize, long totalSize) {
        return UploadSession.builder()
                .id(id)
                .userId(ownerId)
                .uploadType(UploadType.NEW_FILE)
                .fileName("movie.mp4")
                .mimeType("video/mp4")
                .totalSize(totalSize)
                .chunkSize(chunkSize)
                .totalParts(totalParts)
                .objectKey("dropbox-files/" + ownerId + "/" + id + "/data")
                .status(status)
                .build();
    }

    @Test
    void rejectsUnknownOrUnownedUpload() {
        UUID ownerId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(ownerId, uploadId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsInvalidState() {
        UUID ownerId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        UploadSession session = session(uploadId, ownerId, UploadStatus.FAILED, 2, 1000, 1500);
        when(uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.complete(ownerId, uploadId))
                .isInstanceOf(InvalidUploadStateException.class);
    }

    @Test
    void rejectsWhenPartsMissing() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        UploadSession session = session(uploadId, ownerId, UploadStatus.UPLOADING, 3, 1000, 2500);
        when(uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)).thenReturn(Optional.of(session));
        when(uploadPartRepository.findPartNumbersByUploadSessionId(uploadId)).thenReturn(List.of(1, 2));

        assertThatThrownBy(() -> service.complete(ownerId, uploadId))
                .isInstanceOf(InvalidRequestException.class);

        verify(minioClient, never()).composeObject(any());
        verify(metadataServiceClient, never()).materializeFile(any(), any());
    }

    @Test
    void completesSuccessfullyFromUploading() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        UploadSession session = session(uploadId, ownerId, UploadStatus.UPLOADING, 2, 1000, 1500);
        UUID fileId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        when(uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)).thenReturn(Optional.of(session));
        when(uploadPartRepository.findPartNumbersByUploadSessionId(uploadId)).thenReturn(List.of(1, 2));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(minioClient.removeObjects(any())).thenReturn(List.of());
        when(metadataServiceClient.materializeFile(eq(ownerId), any(MaterializeFileRequest.class)))
                .thenReturn(new MaterializeFileResponse(fileId, versionId, 1));

        CompleteUploadResponse response = service.complete(ownerId, uploadId);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.fileId()).isEqualTo(fileId);
        assertThat(response.versionId()).isEqualTo(versionId);
        assertThat(session.getStatus()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(session.getFileId()).isEqualTo(fileId);

        verify(minioClient, times(1)).composeObject(any());

        ArgumentCaptor<MaterializeFileRequest> requestCaptor = ArgumentCaptor.forClass(MaterializeFileRequest.class);
        verify(metadataServiceClient).materializeFile(eq(ownerId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().sourceUploadId()).isEqualTo(uploadId);
        assertThat(requestCaptor.getValue().objectKey()).isEqualTo(session.getObjectKey());
    }

    @Test
    void resumesFromStorageCompletedWithoutRecomposing() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        UploadSession session = session(uploadId, ownerId, UploadStatus.STORAGE_COMPLETED, 2, 1000, 1500);
        UUID fileId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        when(uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)).thenReturn(Optional.of(session));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(metadataServiceClient.materializeFile(eq(ownerId), any(MaterializeFileRequest.class)))
                .thenReturn(new MaterializeFileResponse(fileId, versionId, 1));

        CompleteUploadResponse response = service.complete(ownerId, uploadId);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(session.getStatus()).isEqualTo(UploadStatus.COMPLETED);

        verify(uploadPartRepository, never()).findPartNumbersByUploadSessionId(any());
        verify(minioClient, never()).composeObject(any());
        verify(minioClient, never()).removeObjects(any());
        verify(metadataServiceClient, times(1)).materializeFile(any(), any());
    }

    @Test
    void repeatedCompletionReplaysWithoutSideEffects() {
        UUID ownerId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UploadSession session = session(uploadId, ownerId, UploadStatus.COMPLETED, 2, 1000, 1500);
        session.setFileId(fileId);
        when(uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)).thenReturn(Optional.of(session));

        CompleteUploadResponse response = service.complete(ownerId, uploadId);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.fileId()).isEqualTo(fileId);

        verify(uploadSessionRepository, never()).save(any());
        verify(minioClient, never()).composeObject(any());
        verify(metadataServiceClient, never()).materializeFile(any(), any());
    }
}
