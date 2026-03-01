package com.bugisiw.marketplace.common.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Provides a RestTemplate for health checks with short timeouts so dependency pings do not block.
 * Services can override by defining their own bean named "healthCheckRestTemplate".
 */
@Configuration
public class HealthCheckRestTemplateConfig {

    @Bean(name = "healthCheckRestTemplate")
    @ConditionalOnMissingBean(name = "healthCheckRestTemplate")
    public RestTemplate healthCheckRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(2))
                .build();
    }
}
