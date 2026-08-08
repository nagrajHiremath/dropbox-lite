package com.dropbox.upload_service.service;

import com.dropbox.upload_service.domain.UploadSession;
import io.minio.MinioClient;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deletes the deterministic temporary part objects (see UploadObjectKeys) a
 * session's parts were uploaded to. Shared by UploadCompletionService (after a
 * successful compose) and UploadAbortService (see UPL-07's "MinIO multipart
 * aborted" - under the compose-object pattern, "abort" means discarding these
 * temp objects, since there is no native MinIO multipart session to abort).
 *
 * Always best-effort: callers should treat cleanup failure as non-fatal to
 * whatever durable state transition (STORAGE_COMPLETED, ABORTED) already
 * succeeded - a failure here just leaves harmless orphaned temp objects.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class UploadTempPartsCleaner {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    void cleanup(UploadSession session) {
        try {
            List<DeleteObject> toDelete = new ArrayList<>();
            for (int partNumber = 1; partNumber <= session.getTotalParts(); partNumber++) {
                toDelete.add(new DeleteObject(UploadObjectKeys.partObjectKey(session, partNumber)));
            }
            Iterable<Result<DeleteError>> results = minioClient.removeObjects(
                    RemoveObjectsArgs.builder().bucket(bucket).objects(toDelete).build());
            for (Result<DeleteError> result : results) {
                result.get(); // force evaluation to actually issue the delete
            }
        } catch (Exception e) {
            log.warn("Failed to clean up temporary parts for upload {}: {}", session.getId(), e.getMessage());
        }
    }
}
