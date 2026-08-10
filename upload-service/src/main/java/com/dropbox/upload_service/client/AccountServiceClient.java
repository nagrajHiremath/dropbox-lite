package com.dropbox.upload_service.client;

import com.dropbox.upload_service.exception.DependencyUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Thin synchronous client to Account Service, addressed via k8s Service DNS
 * in-cluster (or localhost in local dev) - see account-service.url in
 * application.yaml. Used only for quota enforcement at upload-initiation
 * time (UploadInitiationService); calls account-service's internal-only
 * /api/v1/internal/users/{userId}/storage endpoint, which carries no gateway
 * route and no separate identity header - the userId path segment is
 * already the full identity needed.
 */
@Component
public class AccountServiceClient {

    private final RestClient restClient;

    public AccountServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${account-service.url}") String accountServiceUrl) {
        this.restClient = restClientBuilder.baseUrl(accountServiceUrl).build();
    }

    /**
     * @throws DependencyUnavailableException if account-service could not be reached or
     *                                         returned an unexpected error - deliberately fails
     *                                         closed (upload initiation aborts) rather than
     *                                         proceeding uncoordinated when quota can't be checked.
     */
    public StorageUsage getStorageUsage(UUID userId) {
        try {
            StorageUsage usage = restClient.get()
                    .uri("/api/v1/internal/users/{userId}/storage", userId)
                    .retrieve()
                    .body(StorageUsage.class);
            if (usage == null) {
                throw new DependencyUnavailableException("Account service returned an empty storage usage response");
            }
            return usage;
        } catch (RestClientException e) {
            throw new DependencyUnavailableException("Account service is unreachable", e);
        }
    }

    public record StorageUsage(long usedBytes, long maxBytes) {
    }
}
