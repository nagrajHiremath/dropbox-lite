package com.dropbox.download_service.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * metadata-service is addressed directly via k8s Service DNS in-cluster
     * (or localhost in local dev), not resolved through Eureka/Spring Cloud
     * LoadBalancer - so there's no longer a need for a separate @LoadBalanced
     * builder, or a plain one kept aside to protect Eureka's own transport
     * from being load-balanced.
     *
     * OBS-01: forwards the current request's X-Request-Id (see RequestIdFilter)
     * onto outbound calls to metadata-service, so a single caller request is
     * traceable across both services' logs via the same id.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder().requestInterceptor((request, body, execution) -> {
            String requestId = MDC.get("requestId");
            if (requestId != null) {
                request.getHeaders().add("X-Request-Id", requestId);
            }
            return execution.execute(request, body);
        });
    }
}
