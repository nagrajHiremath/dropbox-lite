package com.dropbox.async_worker.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin synchronous client to Upload Service, addressed via k8s Service DNS
 * in-cluster (or localhost in local dev) - see upload-service.url in
 * application.yaml.
 */
@Component
public class UploadServiceClient {

    /**
     * The expired-upload sweep is not scoped to any single end user - it acts on
     * sessions across all owners - but Upload Service's internal endpoints all
     * authenticate via the same trusted X-User-Id header (see
     * InternalUploadMaintenanceController), so the sweep sends this fixed
     * sentinel value purely to satisfy that filter. It is never used to look up
     * or scope any data.
     */
    private static final String SYSTEM_PRINCIPAL_ID = "00000000-0000-0000-0000-000000000000";

    private final RestClient restClient;

    public UploadServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${upload-service.url}") String uploadServiceUrl) {
        this.restClient = restClientBuilder.baseUrl(uploadServiceUrl).build();
    }

    /**
     * Triggers Upload Service's expired-session sweep and returns how many
     * sessions it expired.
     */
    public int triggerExpireSweep() {
        ExpireSweepResponse response = restClient.post()
                .uri("/api/v1/internal/uploads/expire-sweep")
                .header("X-User-Id", SYSTEM_PRINCIPAL_ID)
                .retrieve()
                .body(ExpireSweepResponse.class);
        return response != null ? response.expiredCount() : 0;
    }

    private record ExpireSweepResponse(int expiredCount) {
    }
}
