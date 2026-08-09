package com.dropbox.metadata_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * RDS-01/RDS-02: generic cache-aside helper over the existing auto-configured
 * StringRedisTemplate (already used by RedisLockService). Every operation
 * swallows Redis failures (logs a warning, falls through as a miss/no-op)
 * rather than propagating them - "Redis outage falls back to DB" and "Never
 * make successful Redis invalidation part of the database correctness
 * guarantee" (TECHNICAL_DESIGN.md 42/43) require this, which is why this uses
 * an explicit wrapper rather than Spring's @Cacheable (whose default error
 * handling propagates a cache-store exception and fails the request).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("Redis cache read failed for key {}, falling back to DB: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Redis cache write failed for key {}: {}", key, e.getMessage());
        }
    }

    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis cache evict failed for key {}: {}", key, e.getMessage());
        }
    }

    public void evictByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis cache pattern-evict failed for pattern {}: {}", pattern, e.getMessage());
        }
    }
}
