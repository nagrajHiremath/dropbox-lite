package com.dropbox.api_gateway.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * RDS-03: replaces Spring Cloud Gateway's built-in RequestRateLimiter /
 * RedisRateLimiter with one of our own RateLimiter implementations (the
 * active one selected via app.rate-limit.method). Route yaml shape is
 * unchanged in spirit - a per-route filter with args - just renamed
 * max-requests/window-seconds/key-resolver instead of
 * redis-rate-limiter.replenishRate/burstCapacity/key-resolver.
 *
 * Sets Retry-After directly from RateLimitResult on a 429, since (unlike
 * RedisRateLimiter) that value is already exact - no need for
 * RetryAfterHeaderFilter's constant-second fallback anymore.
 *
 * Redis key is scoped by route id + resolved key here, not just the resolved
 * key alone - RateLimiter implementations only ever see this already-scoped
 * string, so upload/download/metadata routes (all on userKeyResolver) each
 * get their own counter instead of silently sharing one per user.
 */
@Component
public class CustomRateLimiterGatewayFilterFactory
        extends AbstractGatewayFilterFactory<CustomRateLimiterGatewayFilterFactory.Config> {

    private final RateLimiter rateLimiter;

    public CustomRateLimiterGatewayFilterFactory(RateLimiter rateLimiter) {
        super(Config.class);
        this.rateLimiter = rateLimiter;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> config.getKeyResolver().resolve(exchange)
                .flatMap(key -> {
                    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    String scopedKey = (route != null ? route.getId() : "unknown-route") + ":" + key;

                    RateLimitResult result = rateLimiter.check(scopedKey, config.getMaxRequests(), config.getWindowSeconds());

                    exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(result.remaining()));

                    if (!result.isAllowed()) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().add(HttpHeaders.RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
                        return exchange.getResponse().setComplete();
                    }

                    return chain.filter(exchange);
                });
    }

    @Getter
    @Setter
    public static class Config {
        private int maxRequests;
        private long windowSeconds;
        private KeyResolver keyResolver;
    }
}
