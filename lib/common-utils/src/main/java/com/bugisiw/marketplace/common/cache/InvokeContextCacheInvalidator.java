package com.bugisiw.marketplace.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Set;

/**
 * Invalidates invoke-context cache entries by agentKey.
 * Uses the same Redis key pattern as the gateway's InvokeContextCacheService:
 * index key {@code invoke-context:keys:{agentKey}} holds a set of cache keys to delete.
 * Used by gateway (delegation) and by gateway-usage-consumer after successful /usage/record.
 */
@RequiredArgsConstructor
@Slf4j
public class InvokeContextCacheInvalidator {

    private static final String INDEX_PREFIX = "invoke-context:keys:";

    private final JedisPool jedisPool;

    /**
     * Invalidates all cached invoke-context entries for this agentKey.
     * No-op if agentKey is null.
     */
    public void invalidateForAgentKey(String agentKey) {
        if (agentKey == null) {
            return;
        }
        String indexKey = INDEX_PREFIX + agentKey;
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.smembers(indexKey);
            if (keys != null && !keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
                log.debug("Invalidated {} invoke-context cache entries for agentKey", keys.size());
            }
            jedis.del(indexKey);
        } catch (Exception e) {
            log.warn("Invoke-context cache invalidation failed for agentKey: {}", e.getMessage());
        }
    }
}
