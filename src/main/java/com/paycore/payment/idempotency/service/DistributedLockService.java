package com.paycore.payment.idempotency.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

/**
 * Distributed locking service backed by Redis with automated circuit breaking.
 * <p>
 * ARCHITECTURAL ROLE & LIMITATIONS:
 * 1. Redis serves as an optional fast-path coordination layer to serialize concurrent requests.
 * 2. REDIS IS NOT THE SOURCE OF TRUTH. The authoritative guarantee against duplicate payments
 *    is enforced by the MySQL unique constraint uk_merchant_idempotency_key on (merchant_id, idempotency_key).
 * 3. If Redis is unavailable or disabled, an automated circuit breaker trips, instantly falling back
 *    to database-level synchronization without latency spikes. Redis failure will NEVER permit duplicates.
 * 4. Lock release is safe: a Lua script ensures that a thread only releases the lock if its token matches,
 *    preventing slow processes from accidentally releasing locks acquired by subsequent operations.
 */
@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);

    private static final String RELEASE_LOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    private static final long CIRCUIT_BREAKER_RESET_MS = 30000;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> releaseScript;
    private final boolean redisEnabled;

    private volatile boolean redisAvailable = true;
    private volatile long lastFailureTimestamp = 0;

    public DistributedLockService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Value("${paycore.redis.enabled:true}") boolean redisEnabled
    ) {
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisEnabled;
        this.releaseScript = new DefaultRedisScript<>();
        this.releaseScript.setScriptText(RELEASE_LOCK_LUA);
        this.releaseScript.setResultType(Long.class);
    }

    /**
     * Attempts to acquire a distributed lock in Redis.
     *
     * @param lockKey   the lock identifier
     * @param lockToken unique token held by the caller
     * @param ttl       time-to-live after which lock auto-expires
     * @return true if acquired; false if contested, disabled, or Redis unavailable
     */
    public boolean acquireLock(String lockKey, String lockToken, Duration ttl) {
        if (!redisEnabled || redisTemplate == null) {
            return false;
        }

        // Circuit breaker: skip Redis if recently failed
        if (!redisAvailable && (System.currentTimeMillis() - lastFailureTimestamp < CIRCUIT_BREAKER_RESET_MS)) {
            return false;
        }

        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, ttl);
            redisAvailable = true;
            boolean result = Boolean.TRUE.equals(acquired);
            if (result) {
                log.debug("Acquired Redis distributed lock [{}]", lockKey);
            } else {
                log.debug("Redis distributed lock [{}] contested", lockKey);
            }
            return result;
        } catch (Exception ex) {
            redisAvailable = false;
            lastFailureTimestamp = System.currentTimeMillis();
            log.warn("Redis unavailable during lock acquisition for [{}]: {}. Tripping circuit-breaker; falling back to DB unique constraint.",
                    lockKey, ex.getMessage());
            return false;
        }
    }

    /**
     * Safely releases the lock if and only if the current lock value matches the caller's token.
     *
     * @param lockKey   the lock identifier
     * @param lockToken token that was used when acquiring the lock
     */
    public void releaseLock(String lockKey, String lockToken) {
        if (!redisEnabled || redisTemplate == null || !redisAvailable) {
            return;
        }

        try {
            Long result = redisTemplate.execute(releaseScript, Collections.singletonList(lockKey), lockToken);
            if (result != null && result > 0) {
                log.debug("Safely released Redis distributed lock [{}]", lockKey);
            } else {
                log.debug("Redis lock [{}] not released: token mismatch or already expired", lockKey);
            }
        } catch (Exception ex) {
            redisAvailable = false;
            lastFailureTimestamp = System.currentTimeMillis();
            log.warn("Redis unavailable during lock release for [{}]: {}", lockKey, ex.getMessage());
        }
    }

    public boolean isRedisAvailable() {
        return redisEnabled && redisAvailable;
    }
}
