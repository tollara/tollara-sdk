package com.bugisiw.marketplace.common.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Health indicator that pings the Redis dependency via Jedis PING.
 * Reports UP when reachable, DOWN when not. Does not affect ALB health when using
 * the liveness group ({@code /actuator/health/liveness}).
 */
@Slf4j
public class RedisHealthIndicator implements HealthIndicator {

    private final JedisPool jedisPool;
    private final String redisHost;
    private final int redisPort;

    public RedisHealthIndicator(JedisPool jedisPool, String redisHost, int redisPort) {
        this.jedisPool = jedisPool;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }

    @Override
    public Health health() {
        try (Jedis jedis = jedisPool.getResource()) {
            String pong = jedis.ping();
            boolean reachable = "PONG".equalsIgnoreCase(pong);
            if (reachable) {
                return Health.up()
                        .withDetail("reachable", true)
                        .withDetail("message", "OK")
                        .withDetail("host", redisHost)
                        .withDetail("port", redisPort)
                        .build();
            }
            return Health.down()
                    .withDetail("reachable", false)
                    .withDetail("message", pong)
                    .withDetail("host", redisHost)
                    .withDetail("port", redisPort)
                    .build();
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : "Unknown error";
            log.trace("Redis health ping failed: {}", message);
            return Health.down()
                    .withDetail("reachable", false)
                    .withDetail("message", message)
                    .withDetail("host", redisHost)
                    .withDetail("port", redisPort)
                    .build();
        }
    }
}
