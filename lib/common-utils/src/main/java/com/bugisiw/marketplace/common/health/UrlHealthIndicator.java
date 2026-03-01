package com.bugisiw.marketplace.common.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.function.Supplier;

/**
 * Health indicator that pings a dependency by URL (e.g. another service's actuator health).
 * Reports UP when the URL returns 2xx, DOWN otherwise.
 * Does not affect ALB health when using the liveness group ({@code /actuator/health/liveness}).
 * <p>
 * Register as a {@code @Bean} with a name like {@code coreServiceHealthIndicator} so the
 * component appears as "coreService" in the health response.
 */
@Slf4j
public class UrlHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;
    private final Supplier<String> healthUrlSupplier;

    public UrlHealthIndicator(RestTemplate restTemplate, Supplier<String> healthUrlSupplier) {
        this.restTemplate = restTemplate;
        this.healthUrlSupplier = healthUrlSupplier;
    }

    @Override
    public Health health() {
        String healthUrl = healthUrlSupplier.get();
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    healthUrl,
                    HttpMethod.GET,
                    null,
                    String.class
            );
            boolean reachable = response.getStatusCode().is2xxSuccessful();
            if (reachable) {
                return Health.up()
                        .withDetail("reachable", true)
                        .withDetail("message", "OK")
                        .withDetail("url", healthUrl)
                        .build();
            }
            return Health.down()
                    .withDetail("reachable", false)
                    .withDetail("message", "HTTP " + response.getStatusCode())
                    .withDetail("url", healthUrl)
                    .build();
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : "Unknown error";
            log.trace("Health ping failed for {}: {}", healthUrl, message);
            return Health.down()
                    .withDetail("reachable", false)
                    .withDetail("message", message)
                    .withDetail("url", healthUrl)
                    .build();
        }
    }
}
