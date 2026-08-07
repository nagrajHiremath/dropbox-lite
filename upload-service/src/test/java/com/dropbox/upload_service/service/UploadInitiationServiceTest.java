package com.dropbox.upload_service.service;

import com.dropbox.upload_service.client.MetadataServiceClient;
import com.dropbox.upload_service.domain.IdempotencyKey;
import com.dropbox.upload_service.domain.UploadSession;
import com.dropbox.upload_service.dto.InitiateUploadRequest;
import com.dropbox.upload_service.dto.InitiateUploadResponse;
import com.dropbox.upload_service.exception.DependencyUnavailableException;
import com.dropbox.upload_service.exception.IdempotencyConflictException;
import com.dropbox.upload_service.repository.UploadSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadInitiationServiceTest {

    @Mock
    private UploadSessionRepository uploadSessionRepository;
    @Mock
    private IdempotencyKeyWriter idempotencyKeyWriter;
    @Mock
    private MetadataServiceClient metadataServiceClient;
    @Mock
    private MinioClient minioClient;

    private UploadInitiationService service;

    @BeforeEach
    void setUp() {
        service = new UploadInitiationService(
                uploadSessionRepository, idempotencyKeyWriter, metadataServiceClient,
                minioClient, new ObjectMapper());
        ReflectionTestUtils.setField(service, "bucket", "dropbox-files");
        ReflectionTestUtils.setField(service, "sessionExpirationHours", 24L);
    }

    private InitiateUploadRequest validRequest() {
        return new InitiateUploadRequest("movie.mp4", null, 1_073_741_824L, "video/mp4");
    }

    @Test
    void initiatesUploadSuccessfully() throws Exception {
        UUID ownerId = UUID.randomUUID();
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(uploadSessionRepository.save(any(UploadSession.class)))
                .thenAnswer(invocation -> {
                    UploadSession session = invocation.getArgument(0);
                    session.setId(UUID.randomUUID());
                    return session;
                });

        InitiateUploadResponse response = service.initiate(ownerId, validRequest(), null);

        assertThat(response.uploadId()).isNotNull();
        assertThat(response.chunkSize()).isEqualTo(8L * 1024 * 1024);
        assertThat(response.totalParts()).isEqualTo(128);
        assertThat(response.status()).isEqualTo("INITIATED");
        verify(idempotencyKeyWriter, never()).reserve(any());
    }

    @Test
    void rejectsMinioFailureWithoutPersistingSession() throws Exception {
        UUID ownerId = UUID.randomUUID();
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> service.initiate(ownerId, validRequest(), null))
                .isInstanceOf(DependencyUnavailableException.class);

        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void replaysSameResultForDuplicateIdempotencyKey() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String idempotencyKey = "key-123";
        ObjectMapper objectMapper = new ObjectMapper();
        InitiateUploadResponse storedResponse =
                new InitiateUploadResponse(UUID.randomUUID(), 8L * 1024 * 1024, 128, "INITIATED");
        InitiateUploadRequest request = validRequest();

        String requestHash = (String) ReflectionTestUtils.invokeMethod(service, "hashRequest", ownerId, request);

        IdempotencyKey existing = IdempotencyKey.builder()
                .userId(ownerId)
                .idempotencyKey(idempotencyKey)
                .operation("INITIATE_UPLOAD")
                .requestHash(requestHash)
                .responseBody(objectMapper.writeValueAsString(storedResponse))
                .build();

        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(idempotencyKeyWriter).reserve(any());
        when(idempotencyKeyWriter.find(ownerId, "INITIATE_UPLOAD", idempotencyKey))
                .thenReturn(Optional.of(existing));

        InitiateUploadResponse response = service.initiate(ownerId, request, idempotencyKey);

        assertThat(response).isEqualTo(storedResponse);
        verify(minioClient, never()).bucketExists(any());
        verify(uploadSessionRepository, never()).save(any());
    }

    @Test
    void rejectsReusedIdempotencyKeyWithDifferentRequest() {
        UUID ownerId = UUID.randomUUID();
        String idempotencyKey = "key-123";

        IdempotencyKey existing = IdempotencyKey.builder()
                .userId(ownerId)
                .idempotencyKey(idempotencyKey)
                .operation("INITIATE_UPLOAD")
                .requestHash("some-other-hash-that-will-never-match")
                .responseBody("{}")
                .build();

        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(idempotencyKeyWriter).reserve(any());
        when(idempotencyKeyWriter.find(ownerId, "INITIATE_UPLOAD", idempotencyKey))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.initiate(ownerId, validRequest(), idempotencyKey))
                .isInstanceOf(IdempotencyConflictException.class);
    }
}
