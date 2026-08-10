package com.dropbox.async_worker.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * metadata-service/upload-service are addressed directly via k8s Service
     * DNS in-cluster (or localhost in local dev), not resolved through Eureka/
     * Spring Cloud LoadBalancer - so there's no longer a need for a separate
     * @LoadBalanced builder, or a plain one kept aside to protect Eureka's own
     * transport from being load-balanced.
     *
     * OBS-01: forwards the current Kafka event's id as X-Request-Id (see
     * PermanentDeletionConsumer, which seeds MDC "requestId" = eventId for the
     * duration of message processing) onto outbound calls to metadata-service/
     * upload-service, so the whole async-triggered chain shares one correlating id.
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
