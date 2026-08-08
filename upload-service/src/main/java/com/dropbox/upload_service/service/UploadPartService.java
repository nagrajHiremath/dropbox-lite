package com.dropbox.upload_service.service;

import com.dropbox.upload_service.domain.UploadPart;
import com.dropbox.upload_service.domain.UploadPartStatus;
import com.dropbox.upload_service.domain.UploadSession;
import com.dropbox.upload_service.domain.UploadStatus;
import com.dropbox.upload_service.dto.UploadPartResponse;
import com.dropbox.upload_service.exception.DependencyUnavailableException;
import com.dropbox.upload_service.exception.InvalidRequestException;
import com.dropbox.upload_service.exception.InvalidUploadStateException;
import com.dropbox.upload_service.exception.ResourceNotFoundException;
import com.dropbox.upload_service.repository.UploadPartRepository;
import com.dropbox.upload_service.repository.UploadSessionRepository;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadPartService {

    private static final Set<UploadStatus> ACCEPTS_PARTS = Set.of(UploadStatus.INITIATED, UploadStatus.UPLOADING);

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadPartRepository uploadPartRepository;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public UploadPartResponse uploadPart(UUID ownerId, UUID uploadId, int partNumber, InputStream partStream) {
        UploadSession session = uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload not found"));

        if (!ACCEPTS_PARTS.contains(session.getStatus())) {
            throw new InvalidUploadStateException(
                    "Upload is not accepting parts in its current state: " + session.getStatus());
        }

        if (partNumber < 1 || partNumber > session.getTotalParts()) {
            throw new InvalidRequestException(
                    "Invalid part number " + partNumber + "; must be between 1 and " + session.getTotalParts());
        }

        long expectedSize = expectedPartSize(session, partNumber);
        String partObjectKey = UploadObjectKeys.partObjectKey(session, partNumber);

        ObjectWriteResponse writeResponse;
        String checksum;
        try {
            DigestInputStream digestStream = new DigestInputStream(partStream, sha256());
            writeResponse = minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(partObjectKey)
                    .stream(digestStream, expectedSize, -1)
                    .build());
            checksum = HexFormat.of().formatHex(digestStream.getMessageDigest().digest());
        } catch (Exception e) {
            throw new DependencyUnavailableException("Failed to stream part to storage backend", e);
        }

        persistPart(session.getId(), partNumber, writeResponse.etag(), checksum, expectedSize);
        markUploading(session);

        return new UploadPartResponse(partNumber, writeResponse.etag(), expectedSize, checksum, UploadPartStatus.UPLOADED.name());
    }

    private void markUploading(UploadSession session) {
        if (session.getStatus() == UploadStatus.INITIATED) {
            session.setStatus(UploadStatus.UPLOADING);
            uploadSessionRepository.save(session);
        }
    }

    private void persistPart(UUID uploadSessionId, int partNumber, String etag, String checksum, long sizeBytes) {
        UploadPart part = uploadPartRepository.findByUploadSessionIdAndPartNumber(uploadSessionId, partNumber)
                .orElseGet(() -> UploadPart.builder()
                        .uploadSessionId(uploadSessionId)
                        .partNumber(partNumber)
                        .build());
        part.setEtag(etag);
        part.setChecksum(checksum);
        part.setSizeBytes(sizeBytes);
        part.setStatus(UploadPartStatus.UPLOADED);

        try {
            uploadPartRepository.save(part);
        } catch (DataIntegrityViolationException e) {
            // A concurrent retry of the same part raced us to the insert. Both attempts
            // uploaded to the same deterministic MinIO object key, so either result is
            // valid - reload and overwrite with ours rather than failing the request.
            UploadPart existing = uploadPartRepository.findByUploadSessionIdAndPartNumber(uploadSessionId, partNumber)
                    .orElseThrow(() -> e);
            existing.setEtag(etag);
            existing.setChecksum(checksum);
            existing.setSizeBytes(sizeBytes);
            existing.setStatus(UploadPartStatus.UPLOADED);
            uploadPartRepository.save(existing);
        }
    }

    private long expectedPartSize(UploadSession session, int partNumber) {
        if (partNumber == session.getTotalParts()) {
            return session.getTotalSize() - session.getChunkSize() * (long) (session.getTotalParts() - 1);
        }
        return session.getChunkSize();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
