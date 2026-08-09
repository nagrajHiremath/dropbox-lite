package com.dropbox.async_worker.config;

import org.slf4j.MDC;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * Plain, non-load-balanced builder. Kept @Primary and unqualified so that
     * Eureka's own registration/heartbeat transport picks this one rather than
     * the load-balanced bean below. See upload-service's RestClientConfig for
     * the full explanation - a lone @LoadBalanced builder gets picked up by
     * Eureka's own client and breaks self-registration.
     */
    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * OBS-01: forwards the current Kafka event's id as X-Request-Id (see
     * PermanentDeletionConsumer, which seeds MDC "requestId" = eventId for the
     * duration of message processing) onto outbound calls to metadata-service/
     * upload-service, so the whole async-triggered chain shares one correlating id.
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder().requestInterceptor((request, body, execution) -> {
            String requestId = MDC.get("requestId");
            if (requestId != null) {
                request.getHeaders().add("X-Request-Id", requestId);
            }
            return execution.execute(request, body);
        });
    }
}
