package com.dropbox.download_service.client;

import com.dropbox.download_service.dto.FileContentInfoResponse;
import com.dropbox.download_service.exception.DependencyUnavailableException;
import com.dropbox.download_service.exception.ResourceNotFoundException;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

/**
 * Thin synchronous client to Metadata Service, resolved via Eureka. Download
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
     * Resolves the current version's storage reference for a file.
     *
     * @throws ResourceNotFoundException      if the file does not exist, is not owned by the caller, or has no content
     * @throws DependencyUnavailableException if metadata-service could not be reached
     */
    public FileContentInfoResponse getCurrentVersion(UUID ownerId, UUID fileId) {
        try {
            return restClient.get()
                    .uri("/api/v1/internal/files/{fileId}/current-version", fileId)
                    .header("X-User-Id", ownerId.toString())
                    .retrieve()
                    .body(FileContentInfoResponse.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException("File not found");
            }
            throw new DependencyUnavailableException("Metadata service returned an unexpected error", e);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException("Metadata service is unreachable", e);
        }
    }
}
