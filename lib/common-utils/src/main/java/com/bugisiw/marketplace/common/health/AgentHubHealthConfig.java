package com.bugisiw.marketplace.common.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import redis.clients.jedis.JedisPool;

/**
 * Shared health configuration: health-check RestTemplate, Redis health indicator,
 * and dependency secure health (for /actuator/health/dependency).
 * Services that want shared health support should {@code @Import(AgentHubHealthConfig.class)}.
 */
@Configuration
@Import(HealthCheckRestTemplateConfig.class)
public class AgentHubHealthConfig {

    @Bean("dependencySecureCheck")
    public DependencySecureHealthIndicator dependencySecureHealthIndicator() {
        return new DependencySecureHealthIndicator();
    }

    @Bean
    @ConditionalOnBean(JedisPool.class)
    public RedisHealthIndicator redisHealthIndicator(JedisPool jedisPool,
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        return new RedisHealthIndicator(jedisPool, host, port);
    }
}
