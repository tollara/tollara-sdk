package com.bugisiw.marketplace.common.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator for the secured dependency health endpoint.
 * Returns UP when this service is running; used so callers can GET
 * /actuator/health/dependency with a service token to verify connectivity and auth.
 * Register in health group "dependency" via management.endpoint.health.group.dependency.include.
 */
public class DependencySecureHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        return Health.up().withDetail("message", "Service is reachable with valid token").build();
    }
}
