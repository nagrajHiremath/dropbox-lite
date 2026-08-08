package com.dropbox.async_worker.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin synchronous client to Upload Service, resolved via Eureka.
 */
@Component
public class UploadServiceClient {

    private static final String UPLOAD_SERVICE_BASE_URL = "lb://upload-service";

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

    public UploadServiceClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl(UPLOAD_SERVICE_BASE_URL).build();
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
