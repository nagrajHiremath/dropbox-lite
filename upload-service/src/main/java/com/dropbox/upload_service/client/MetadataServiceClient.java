package com.dropbox.upload_service.client;

import com.dropbox.upload_service.dto.FileInfo;
import com.dropbox.upload_service.dto.MaterializeFileRequest;
import com.dropbox.upload_service.dto.MaterializeFileResponse;
import com.dropbox.upload_service.dto.MaterializeVersionRequest;
import com.dropbox.upload_service.exception.DependencyUnavailableException;
import com.dropbox.upload_service.exception.InvalidRequestException;
import com.dropbox.upload_service.exception.ResourceNotFoundException;
import com.dropbox.upload_service.exception.VersionConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

/**
 * Thin synchronous client to Metadata Service, addressed via k8s Service DNS
 * in-cluster (or localhost in local dev) - see metadata-service.url in
 * application.yaml. Upload Service calls it directly (service-to-service),
 * bypassing the API Gateway.
 */
@Component
public class MetadataServiceClient {

    private final RestClient restClient;

    public MetadataServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${metadata-service.url}") String metadataServiceUrl) {
        this.restClient = restClientBuilder.baseUrl(metadataServiceUrl).build();
    }

    /**
     * Verifies the given folder exists and is owned by ownerId.
     *
     * @throws ResourceNotFoundException     if the folder does not exist or is not owned by the caller
     * @throws DependencyUnavailableException if metadata-service could not be reached
     */
    public void requireOwnedFolder(UUID folderId, UUID ownerId) {
        try {
            restClient.get()
                    .uri("/api/v1/folders/{id}", folderId)
                    .header("X-User-Id", ownerId.toString())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException("Folder not found");
            }
            throw new DependencyUnavailableException("Metadata service returned an unexpected error", e);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException("Metadata service is unreachable", e);
        }
    }

    /**
     * Materializes the logical file/version for a completed upload. Idempotent on
     * metadata-service's side, keyed by request.sourceUploadId() - safe to retry.
     *
     * @throws ResourceNotFoundException      if the target folder does not exist or is not owned by the caller
     * @throws InvalidRequestException        if the target folder is not active
     * @throws DependencyUnavailableException if metadata-service could not be reached
     */
    public MaterializeFileResponse materializeFile(UUID ownerId, MaterializeFileRequest request) {
        try {
            return restClient.post()
                    .uri("/api/v1/internal/files")
                    .header("X-User-Id", ownerId.toString())
                    .body(request)
                    .retrieve()
                    .body(MaterializeFileResponse.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404) {
                throw new ResourceNotFoundException("Folder not found");
            }
            if (status == 400) {
                throw new InvalidRequestException("Metadata service rejected the file materialization request");
            }
            throw new DependencyUnavailableException("Metadata service returned an unexpected error", e);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException("Metadata service is unreachable", e);
        }
    }

    /**
     * Validates the target file for a new-version upload (VER-01): must exist,
     * be owned by ownerId, and be ACTIVE. Reuses the same public endpoint the
     * gateway forwards end-user GETs to - CurrentUserHeaderFilter authenticates
     * this trusted service-to-service call identically.
     *
     * @throws ResourceNotFoundException      if the file does not exist, is not owned by the caller, or is not active
     * @throws DependencyUnavailableException if metadata-service could not be reached
     */
    public FileInfo requireOwnedActiveFile(UUID fileId, UUID ownerId) {
        FileInfo info;
        try {
            info = restClient.get()
                    .uri("/api/v1/files/{id}", fileId)
                    .header("X-User-Id", ownerId.toString())
                    .retrieve()
                    .body(FileInfo.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException("File not found");
            }
            throw new DependencyUnavailableException("Metadata service returned an unexpected error", e);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException("Metadata service is unreachable", e);
        }
        if (info == null || !"ACTIVE".equals(info.status())) {
            throw new ResourceNotFoundException("File not found");
        }
        return info;
    }

    /**
     * Materializes a new version of an existing file for a completed NEW_VERSION
     * upload (VER-02). Idempotent on metadata-service's side, keyed by
     * request.sourceUploadId() - safe to retry.
     *
     * @throws ResourceNotFoundException      if the file does not exist or is not owned by the caller
     * @throws InvalidRequestException        if the file is not active
     * @throws VersionConflictException       if a concurrent version upload for the same file won the race
     * @throws DependencyUnavailableException if metadata-service could not be reached
     */
    public MaterializeFileResponse materializeVersion(UUID ownerId, MaterializeVersionRequest request) {
        try {
            return restClient.post()
                    .uri("/api/v1/internal/files/versions")
                    .header("X-User-Id", ownerId.toString())
                    .body(request)
                    .retrieve()
                    .body(MaterializeFileResponse.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404) {
                throw new ResourceNotFoundException("File not found");
            }
            if (status == 400) {
                throw new InvalidRequestException("Metadata service rejected the version materialization request");
            }
            if (status == 409) {
                throw new VersionConflictException("Concurrent version creation detected, please retry");
            }
            throw new DependencyUnavailableException("Metadata service returned an unexpected error", e);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException("Metadata service is unreachable", e);
        }
    }
}
