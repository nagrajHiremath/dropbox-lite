package com.dropbox.download_service.config;

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
     * infrastructure code with its own unqualified RestClient.Builder injection
     * point - notably Spring Cloud Netflix Eureka's own registration/heartbeat
     * transport - picks this one rather than the load-balanced bean below.
     * Without this, Eureka's own calls to its server URL get routed through the
     * load balancer too, which fails with "No instances available for <eureka-host>"
     * since the Eureka server's host is a literal address, not a registered service.
     * (See upload-service's RestClientConfig - hit this exact issue there first.)
     */
    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * OBS-01: forwards the current request's X-Request-Id (see RequestIdFilter)
     * onto outbound calls to metadata-service, so a single caller request is
     * traceable across both services' logs via the same id.
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
