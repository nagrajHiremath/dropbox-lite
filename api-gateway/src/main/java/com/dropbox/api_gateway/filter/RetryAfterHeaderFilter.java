package com.dropbox.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * RDS-03: Spring Cloud Gateway's RedisRateLimiter sets X-RateLimit-* headers
 * on a 429 rejection but not Retry-After. "Retry-After where practical" (not
 * "exact") is satisfied with a constant matching the token bucket's
 * per-second replenish granularity, rather than forking/overriding Gateway's
 * bundled filter to compute a precise value.
 *
 * Uses response.beforeCommit(...) rather than chain.filter(exchange).then(...):
 * a rejected request's response is already committed (and its headers made
 * read-only - ReadOnlyHttpHeaders) by the time any post-chain callback would
 * run, regardless of this filter's order, since RequestRateLimiter calls
 * response.setComplete() directly on rejection without further propagating
 * through the chain. beforeCommit registers a callback against the response
 * itself, invoked right before it actually writes - the one hook point
 * guaranteed to run before headers become immutable, independent of filter
 * ordering or which code path completes the response.
 */
@Component
public class RetryAfterHeaderFilter implements GlobalFilter, Ordered {

    private static final String RETRY_AFTER_SECONDS = "1";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                exchange.getResponse().getHeaders().add(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
