package com.dropbox.upload_service.service;

import com.dropbox.upload_service.domain.UploadSession;
import com.dropbox.upload_service.domain.UploadStatus;
import com.dropbox.upload_service.exception.InvalidUploadStateException;
import com.dropbox.upload_service.exception.ResourceNotFoundException;
import com.dropbox.upload_service.repository.UploadSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Abort under the compose-object pattern (see UPL-02): there is no native
 * MinIO multipart session to abort, so "MinIO multipart aborted" (UPL-07)
 * means discarding the temporary part objects UploadPartService wrote -
 * the same cleanup UploadCompletionService performs after a successful
 * compose, via the shared UploadTempPartsCleaner.
 */
@Service
@RequiredArgsConstructor
public class UploadAbortService {

    private static final Set<UploadStatus> ABORTABLE_STATES = Set.of(
            UploadStatus.INITIATED, UploadStatus.UPLOADING, UploadStatus.FAILED, UploadStatus.EXPIRED);

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadTempPartsCleaner tempPartsCleaner;

    public void abort(UUID ownerId, UUID uploadId) {
        UploadSession session = uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload not found"));

        if (session.getStatus() == UploadStatus.ABORTED) {
            return; // repeated abort is safe
        }

        if (!ABORTABLE_STATES.contains(session.getStatus())) {
            throw new InvalidUploadStateException("Upload cannot be aborted from state " + session.getStatus());
        }

        tempPartsCleaner.cleanup(session);

        session.setStatus(UploadStatus.ABORTED);
        uploadSessionRepository.save(session);
    }
}
