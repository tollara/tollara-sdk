package com.bugisiw.marketplace.client;

import com.bugisiw.marketplace.common.util.HmacUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for validating agent keys via the core-service validation endpoint.
 * Implements caching to reduce load on the validation service.
 */
@Slf4j
public class AgentKeyValidationClient {

    private final String coreServiceUrl;
    private final String agentId;
    private final String agentSecret;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // In-memory cache (can be replaced with Redis in production)
    private final Map<String, CachedValidationResult> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000; // 60 seconds

    /**
     * Creates a new AgentKeyValidationClient.
     *
     * @param coreServiceUrl The base URL of the core-service (e.g., "http://core-service:8081/api/v1")
     * @param agentId The agent ID
     * @param agentSecret The agent's secret key for authentication
     * @param restTemplate RestTemplate for HTTP calls
     */
    public AgentKeyValidationClient(String coreServiceUrl, String agentId, String agentSecret, RestTemplate restTemplate) {
        this.coreServiceUrl = coreServiceUrl;
        this.agentId = agentId;
        this.agentSecret = agentSecret;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Validates an agent key and returns user context.
     * Uses caching to reduce load on the validation service.
     *
     * @param agentKey The agent key to validate
     * @return Validation result with user context, or null if validation fails
     */
    public AgentKeyValidationResult validateAgentKey(String agentKey) {
        if (agentKey == null || agentKey.isEmpty()) {
            log.warn("Agent key is null or empty");
            return null;
        }

        // Check cache first
        CachedValidationResult cached = cache.get(agentKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Cache hit for agent key validation");
            return cached.getResult();
        }

        // Call validation endpoint with retry logic
        String validateUrl = coreServiceUrl + "/agent-keys/validate";
        ValidateRequest request = new ValidateRequest(agentKey, agentId, agentSecret); // agentId can be null
        
        int maxRetries = 5; // Increased from 3 to handle slow BCrypt validation
        long retryDelayMs = 200; // Base delay for exponential backoff (increased from 100ms)
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<ValidateRequest> requestEntity = new HttpEntity<>(request, headers);

                ResponseEntity<ValidationResponse> response = restTemplate.exchange(
                        validateUrl,
                        HttpMethod.POST,
                        requestEntity,
                        ValidationResponse.class
                );

                ValidationResponse validationResponse = response.getBody();
                if (response.getStatusCode().is2xxSuccessful() && validationResponse != null) {

                    // Log the full JSON response
                    String responseJson = objectMapper.writeValueAsString(validationResponse);
                    log.info("Agent key validation response from core-service: {}", responseJson);

                    // Verify HMAC signature from response headers
                    String signature = response.getHeaders().getFirst("X-Marketplace-Signature");
                    String timestampStr = response.getHeaders().getFirst("X-Marketplace-Timestamp");
                    if (signature == null || timestampStr == null) {
                        log.warn("Missing HMAC signature or timestamp in validation response");
                        return null;
                    }

                    long timestamp = Long.parseLong(timestampStr);
                    boolean isValid = HmacUtils.validateHmacSignature(signature, responseJson + timestamp, agentSecret);

                    if (!isValid) {
                        log.warn("Invalid HMAC signature in validation response");
                        return null;
                    }

                    if (!validationResponse.isValid()) {
                        log.warn("Agent key validation failed: {}", validationResponse.getError());
                        return null;
                    }

                    // Build result - use agentId from response if available, otherwise use the one we passed (which might be null)
                    String resultAgentId = validationResponse.getAgentId() != null ? 
                            validationResponse.getAgentId() : (agentId != null ? agentId : null);
                    AgentKeyValidationResult result = AgentKeyValidationResult.builder()
                            .userId(validationResponse.getUserId())
                            .agentId(resultAgentId)
                            .plan(validationResponse.getPlan())
                            .roles(validationResponse.getRoles() != null ? validationResponse.getRoles() : Collections.emptyList())
                            .quotaRemaining(validationResponse.getQuotaRemaining())
                            .subscriptionActive(validationResponse.isSubscriptionActive())
                            .build();
                    
                    log.debug("Agent key validation successful - User: {}, AgentId: {}, Plan: {}", 
                            result.getUserId(), result.getAgentId(), result.getPlan());

                    // Cache the result
                    cache.put(agentKey, new CachedValidationResult(result, System.currentTimeMillis()));

                    return result;
                } else {
                    log.warn("Validation endpoint returned non-2xx status: {}", response.getStatusCode());
                    return null;
                }
            } catch (org.springframework.web.client.ResourceAccessException e) {
                // Handle timeout and connection errors - retry
                log.warn("Timeout/connection error validating agent key, attempt {}/{}: {}", 
                        attempt + 1, maxRetries, e.getMessage());
                if (attempt < maxRetries - 1) {
                    try {
                        // Add small random jitter to spread out retries and reduce thundering herd
                        long jitter = (long)(Math.random() * 20); // 0-20ms random jitter
                        long delay = retryDelayMs * (1L << attempt) + jitter; // Exponential backoff with jitter
                        Thread.sleep(delay);
                        continue;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Interrupted during agent key validation retry");
                        return null;
                    }
                } else {
                    log.error("Error validating agent key after {} attempts: {}", maxRetries, e.getMessage(), e);
                    return null;
                }
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                // Don't retry on client errors (4xx) - these are final
                log.warn("HTTP client error validating agent key: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
                return null;
            } catch (Exception e) {
                // Don't retry on other exceptions (HMAC validation errors, etc.)
                log.error("Error validating agent key: {}", e.getMessage(), e);
                return null;
            }
        }
        
        return null;
    }

    /**
     * Clears the validation cache.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Request DTO for validation endpoint.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ValidateRequest {
        private String agentKey;
        private String agentId;
        private String agentSecret;
    }

    /**
     * Response DTO from validation endpoint.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ValidationResponse {
        private boolean valid;
        private String userId;
        private String agentId;
        private String plan;
        private List<String> roles;
        private BigDecimal quotaRemaining;
        private boolean subscriptionActive;
        private long timestamp;
        private String error;
    }

    /**
     * Result of agent key validation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentKeyValidationResult {
        private String userId;
        private String agentId;
        private String plan;
        private List<String> roles;
        private BigDecimal quotaRemaining;
        private boolean subscriptionActive;
    }

    /**
     * Cached validation result with expiration timestamp.
     */
    private static class CachedValidationResult {
        private final AgentKeyValidationResult result;
        private final long timestamp;

        CachedValidationResult(AgentKeyValidationResult result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }

        AgentKeyValidationResult getResult() {
            return result;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}

