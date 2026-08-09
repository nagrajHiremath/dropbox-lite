package com.dropbox.api_gateway.config;

import com.dropbox.api_gateway.filter.JwtAuthenticationWebFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * RDS-03: KeyResolver beans for the RequestRateLimiter gateway filter (see
 * TECHNICAL_DESIGN.md 44). Referenced from application.yaml route filter args
 * via SpEL, e.g. key-resolver: "#{@userKeyResolver}".
 */
@Configuration
public class RateLimiterKeyResolverConfig {

    /**
     * Used for authenticated routes (upload/download/metadata) - X-User-Id is
     * already set by JwtAuthenticationWebFilter (a plain WebFilter running
     * earlier in the WebFlux chain than Gateway's own route-level filters) by
     * the time this runs. Falls back to remote address defensively, though
     * that shouldn't happen on a route that requires authentication.
     */
    /**
     * @Primary: every route below sets key-resolver explicitly, but Spring
     * Cloud Gateway's RequestRateLimiterGatewayFilterFactory also has its own
     * default KeyResolver injection point (used when a route omits
     * key-resolver) - with two KeyResolver beans and no @Primary, that
     * injection is ambiguous and fails at startup even though nothing here
     * actually relies on the default.
     */
    @Primary
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(JwtAuthenticationWebFilter.USER_ID_HEADER);
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            return Mono.just(remoteAddress(exchange));
        };
    }

    /**
     * Used for pre-authentication routes (public share, auth) where no
     * X-User-Id is ever set - see JwtAuthenticationWebFilter.PUBLIC_PATH_PREFIXES.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(remoteAddress(exchange));
    }

    private static String remoteAddress(ServerWebExchange exchange) {
        var remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null
                ? remote.getAddress().getHostAddress()
                : "unknown";
    }
}
