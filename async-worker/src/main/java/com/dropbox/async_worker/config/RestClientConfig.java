package com.dropbox.async_worker.config;

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

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
