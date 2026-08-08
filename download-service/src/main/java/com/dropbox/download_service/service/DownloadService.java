package com.dropbox.download_service.service;

import com.dropbox.download_service.client.MetadataServiceClient;
import com.dropbox.download_service.dto.FileContentInfoResponse;
import com.dropbox.download_service.exception.DependencyUnavailableException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Owner authorization and current-version resolution happen via Metadata
 * Service (see DNL-01); this class only ever touches bytes, streaming them
 * straight through from MinIO without buffering the whole object in memory.
 */
@Service
@RequiredArgsConstructor
public class DownloadService {

    private final MetadataServiceClient metadataServiceClient;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public ResolvedFile resolve(UUID ownerId, UUID fileId) {
        FileContentInfoResponse info = metadataServiceClient.getCurrentVersion(ownerId, fileId);
        return new ResolvedFile(info.fileName(), info.mimeType(), info.objectKey(), info.sizeBytes());
    }

    public ResolvedFile resolveVersion(UUID ownerId, UUID fileId, UUID versionId) {
        FileContentInfoResponse info = metadataServiceClient.getVersion(ownerId, fileId, versionId);
        return new ResolvedFile(info.fileName(), info.mimeType(), info.objectKey(), info.sizeBytes());
    }

    public ResolvedFile resolvePublicShare(String token) {
        FileContentInfoResponse info = metadataServiceClient.getPublicShareContent(token);
        return new ResolvedFile(info.fileName(), info.mimeType(), info.objectKey(), info.sizeBytes());
    }

    public void streamTo(String objectKey, OutputStream outputStream) throws IOException {
        try (InputStream objectStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
            objectStream.transferTo(outputStream);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyUnavailableException("Failed to stream file from storage backend", e);
        }
    }

    /**
     * Streams only [offset, offset+length) - MinIO itself is asked for just that
     * byte range (via GetObjectArgs.offset/length), not the full object.
     */
    public void streamRange(String objectKey, long offset, long length, OutputStream outputStream) throws IOException {
        try (InputStream objectStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .offset(offset)
                .length(length)
                .build())) {
            objectStream.transferTo(outputStream);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyUnavailableException("Failed to stream file from storage backend", e);
        }
    }

    public record ResolvedFile(String fileName, String mimeType, String objectKey, long sizeBytes) {
    }
}
