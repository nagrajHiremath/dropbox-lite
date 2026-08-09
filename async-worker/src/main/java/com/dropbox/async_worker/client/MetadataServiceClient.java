package com.dropbox.async_worker.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * Thin synchronous client to Metadata Service, resolved via Eureka. WRK-02
 * needs the object keys of every version belonging to a (by now DELETED)
 * file to clean up its MinIO bytes.
 */
@Component
public class MetadataServiceClient {

    private static final String METADATA_SERVICE_BASE_URL = "lb://metadata-service";

    private final RestClient restClient;

    public MetadataServiceClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl(METADATA_SERVICE_BASE_URL).build();
    }

    public List<String> getVersionObjectKeys(UUID ownerId, UUID fileId) {
        FileVersionObjectKeysResponse response = restClient.get()
                .uri("/api/v1/internal/files/{fileId}/versions", fileId)
                .header("X-User-Id", ownerId.toString())
                .retrieve()
                .body(FileVersionObjectKeysResponse.class);
        return response != null ? response.objectKeys() : List.of();
    }

    private record FileVersionObjectKeysResponse(List<String> objectKeys) {
    }
}
