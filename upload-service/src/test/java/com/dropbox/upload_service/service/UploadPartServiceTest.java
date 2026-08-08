package com.dropbox.upload_service.service;

import com.dropbox.upload_service.domain.UploadPart;
import com.dropbox.upload_service.domain.UploadPartStatus;
import com.dropbox.upload_service.domain.UploadSession;
import com.dropbox.upload_service.domain.UploadStatus;
import com.dropbox.upload_service.domain.UploadType;
import com.dropbox.upload_service.dto.UploadPartResponse;
import com.dropbox.upload_service.exception.InvalidRequestException;
import com.dropbox.upload_service.exception.InvalidUploadStateException;
import com.dropbox.upload_service.exception.ResourceNotFoundException;
import com.dropbox.upload_service.repository.UploadPartRepository;
import com.dropbox.upload_service.repository.UploadSessionRepository;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadPartServiceTest {

    @Mock
    private UploadSessionRepository uploadSessionRepository;
    @Mock
    private UploadPartRepository uploadPartRepository;
    @Mock
    private MinioClient minioClient;

    private UploadPartService service;

    @BeforeEach
    void setUp() {
        service = new UploadPartService(uploadSessionRepository, uploadPartRepository, minioClient);
        ReflectionTestUtils.setField(service, "bucket", "dropbox-files");
    }

    private UploadSession session(UUID id, UUID ownerId, UploadStatus status, int totalParts, long chunkSize, long totalSize) {
        return UploadSession.builder()
                .id(id)
                .userId(ownerId)
                .uploadType(UploadType.NEW_FILE)
                .fileName("movie.mp4")
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

        assertThatThrownBy(() -> service.uploadPart(ownerId, uploadId, 1, new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsPartWhenSessionNotAcceptingParts() {
        UUID ownerId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        UploadSession session = session(uploadId, ownerId, UploadStatus.COMPLETED, 1, 11, 11);
        when(uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.uploadPart(ownerId, uploadId, 1, new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(InvalidUploadStateException.class);
    }

    @Test
    void rejectsOutOfRangePartNumber() {
        UUID ownerId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        UploadSession session = session(uploadId, ownerId, UploadStatus.INITIATED, 2, 1000, 1500);
        when(uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.uploadPart(ownerId, uploadId, 3, new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.uploadPart(ownerId, uploadId, 0, new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void uploadsPartSuccessfullyAndTransitionsSessionToUploading() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        UploadSession session = session(uploadId, ownerId, UploadStatus.INITIATED, 1, content.length, content.length);

        when(uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)).thenReturn(Optional.of(session));
        when(uploadPartRepository.findByUploadSessionIdAndPartNumber(uploadId, 1)).thenReturn(Optional.empty());
        when(uploadPartRepository.save(any(UploadPart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

        ObjectWriteResponse writeResponse = mock(ObjectWriteResponse.class);
        when(writeResponse.etag()).thenReturn("mock-etag");
        when(minioClient.putObject(any(PutObjectArgs.class))).thenAnswer(invocation -> {
            PutObjectArgs args = invocation.getArgument(0);
            args.stream().readAllBytes(); // drain, like the real client would, so the digest sees the bytes
            return writeResponse;
        });

        UploadPartResponse response = service.uploadPart(ownerId, uploadId, 1, new ByteArrayInputStream(content));

        String expectedChecksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content));

        assertThat(response.partNumber()).isEqualTo(1);
        assertThat(response.etag()).isEqualTo("mock-etag");
        assertThat(response.sizeBytes()).isEqualTo(content.length);
        assertThat(response.checksum()).isEqualTo(expectedChecksum);
        assertThat(response.status()).isEqualTo(UploadPartStatus.UPLOADED.name());
        assertThat(session.getStatus()).isEqualTo(UploadStatus.UPLOADING);

        ArgumentCaptor<UploadPart> partCaptor = ArgumentCaptor.forClass(UploadPart.class);
        verify(uploadPartRepository).save(partCaptor.capture());
        assertThat(partCaptor.getValue().getEtag()).isEqualTo("mock-etag");
        assertThat(partCaptor.getValue().getChecksum()).isEqualTo(expectedChecksum);
        assertThat(partCaptor.getValue().getSizeBytes()).isEqualTo((long) content.length);
    }

    @Test
    void retryingSamePartOverwritesExistingRowInstead() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        byte[] content = "retry".getBytes(StandardCharsets.UTF_8);
        UploadSession session = session(uploadId, ownerId, UploadStatus.UPLOADING, 1, content.length, content.length);

        UploadPart existing = UploadPart.builder()
                .id(UUID.randomUUID())
                .uploadSessionId(uploadId)
                .partNumber(1)
                .etag("stale-etag")
                .checksum("stale-checksum")
                .sizeBytes(999L)
                .status(UploadPartStatus.UPLOADED)
                .build();

        when(uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)).thenReturn(Optional.of(session));
        when(uploadPartRepository.findByUploadSessionIdAndPartNumber(uploadId, 1)).thenReturn(Optional.of(existing));
        when(uploadPartRepository.save(any(UploadPart.class))).thenAnswer(inv -> inv.getArgument(0));

        ObjectWriteResponse writeResponse = mock(ObjectWriteResponse.class);
        when(writeResponse.etag()).thenReturn("fresh-etag");
        when(minioClient.putObject(any(PutObjectArgs.class))).thenAnswer(invocation -> {
            PutObjectArgs args = invocation.getArgument(0);
            args.stream().readAllBytes();
            return writeResponse;
        });

        UploadPartResponse response = service.uploadPart(ownerId, uploadId, 1, new ByteArrayInputStream(content));

        assertThat(response.etag()).isEqualTo("fresh-etag");
        verify(uploadPartRepository, times(1)).save(any(UploadPart.class));

        ArgumentCaptor<UploadPart> partCaptor = ArgumentCaptor.forClass(UploadPart.class);
        verify(uploadPartRepository).save(partCaptor.capture());
        assertThat(partCaptor.getValue()).isSameAs(existing);
        assertThat(partCaptor.getValue().getEtag()).isEqualTo("fresh-etag");
        // session was already UPLOADING, so no redundant session save should occur
        verify(uploadSessionRepository, times(0)).save(any());
    }
}
