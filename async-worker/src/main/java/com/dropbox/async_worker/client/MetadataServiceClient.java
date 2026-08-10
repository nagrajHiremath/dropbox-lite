package com.dropbox.async_worker.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * Thin synchronous client to Metadata Service, addressed via k8s Service DNS
 * in-cluster (or localhost in local dev) - see metadata-service.url in
 * application.yaml. WRK-02 needs the object keys of every version belonging
 * to a (by now DELETED) file to clean up its MinIO bytes.
 */
@Component
public class MetadataServiceClient {

    private final RestClient restClient;

    public MetadataServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${metadata-service.url}") String metadataServiceUrl) {
        this.restClient = restClientBuilder.baseUrl(metadataServiceUrl).build();
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
