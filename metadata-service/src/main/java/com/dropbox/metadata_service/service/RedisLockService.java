package com.dropbox.metadata_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal single-instance Redis distributed lock (SET NX PX + a Lua
 * check-and-delete on release) - see TECHNICAL_DESIGN.md "45. Distributed
 * Locks" / "46. Redis Failure Rule". Not a full Redlock (no multi-node
 * quorum); sufficient for this project's single Redis instance. Mirrors
 * upload-service's RedisLockService (see UPL-06) - kept as this service's own
 * copy rather than a shared library, per this project's "no shared
 * business-domain library" rule.
 *
 * Release uses a token match (not a blind DEL) so a holder whose TTL already
 * expired and was reacquired by someone else can never delete that new
 * holder's lock.
 */
@Component
@RequiredArgsConstructor
public class RedisLockService {

    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else "
                    + "return 0 "
                    + "end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * @return a holder token if the lock was acquired, or empty if another holder already has it.
     */
    public Optional<String> tryAcquire(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    public void release(String key, String token) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
    }
}
