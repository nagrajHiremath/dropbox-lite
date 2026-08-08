package com.dropbox.upload_service.client;

import com.dropbox.upload_service.dto.MaterializeFileRequest;
import com.dropbox.upload_service.dto.MaterializeFileResponse;
import com.dropbox.upload_service.exception.DependencyUnavailableException;
import com.dropbox.upload_service.exception.InvalidRequestException;
import com.dropbox.upload_service.exception.ResourceNotFoundException;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

/**
 * Thin synchronous client to Metadata Service, resolved via Eureka. Upload
 * Service calls it directly (service-to-service), bypassing the API Gateway.
 */
@Component
public class MetadataServiceClient {

    private static final String METADATA_SERVICE_BASE_URL = "lb://metadata-service";

    private final RestClient restClient;

    public MetadataServiceClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl(METADATA_SERVICE_BASE_URL).build();
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
}
