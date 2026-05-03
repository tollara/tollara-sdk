package com.agentvend.client;

import com.agentvend.client.model.UsageEstimateResult;
import com.agentvend.common.util.HmacUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for validating agent keys via the core-service validation endpoint.
 */
@Slf4j
public class AgentKeyValidationClient {

    private final String coreServiceUrl;
    private final String agentId;
    private final String agentSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedValidationResult> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000;

    public AgentKeyValidationClient(String coreServiceUrl, String agentId, String agentSecret, HttpClient httpClient) {
        this.coreServiceUrl = coreServiceUrl;
        this.agentId = agentId;
        this.agentSecret = agentSecret;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Validates an agent key and returns user context. Uses caching (TTL 60s).
     */
    public AgentKeyValidationResult validateAgentKey(String agentKey) {
        if (agentKey == null || agentKey.isEmpty()) {
            log.warn("Agent key is null or empty");
            return null;
        }
        CachedValidationResult cached = cache.get(agentKey);
        if (cached != null && !cached.isExpired()) {
            return cached.getResult();
        }
        String validateUrl = coreServiceUrl + "/agent-keys/validate";
        ValidateRequest request = new ValidateRequest(agentKey, agentId, agentSecret);
        int maxRetries = 5;
        long retryDelayMs = 200;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String bodyJson = objectMapper.writeValueAsString(request);
                HttpResponse<String> response = HttpSupport.postJson(httpClient, validateUrl, bodyJson, Map.of());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String responseText = response.body();
                    String signature =
                            response.headers().firstValue(AgentVendHeaders.SIGNATURE).orElse(null);
                    String timestampStr =
                            response.headers().firstValue(AgentVendHeaders.TIMESTAMP).orElse(null);
                    if (signature == null || timestampStr == null) {
                        log.warn("Missing HMAC signature or timestamp in validation response");
                        return null;
                    }
                    long timestamp = Long.parseLong(timestampStr);
                    if (!HmacUtils.validateHmacSignature(signature, responseText + timestamp, agentSecret)) {
                        log.warn("Invalid HMAC signature in validation response");
                        return null;
                    }
                    ValidationResponse validationResponse =
                            objectMapper.readValue(responseText, ValidationResponse.class);
                    if (!validationResponse.isValid()) {
                        log.warn("Agent key validation failed: {}", validationResponse.getError());
                        return null;
                    }
                    String resultAgentId = validationResponse.getAgentId() != null
                            ? validationResponse.getAgentId() : agentId;
                    AgentKeyValidationResult result = AgentKeyValidationResult.builder()
                            .userId(validationResponse.getUserId())
                            .agentId(resultAgentId)
                            .agentKeyId(validationResponse.getAgentKeyId())
                            .plan(validationResponse.getPlan())
                            .roles(validationResponse.getRoles() != null ? validationResponse.getRoles() : Collections.emptyList())
                            .quotaRemaining(validationResponse.getQuotaRemaining())
                            .subscriptionActive(validationResponse.isSubscriptionActive())
                            .billingModelType(validationResponse.getBillingModelType())
                            .measurementType(validationResponse.getMeasurementType())
                            .unitLabel(validationResponse.getUnitLabel())
                            .build();
                    cache.put(agentKey, new CachedValidationResult(result, System.currentTimeMillis()));
                    return result;
                }
                if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    log.warn("HTTP client error validating agent key: {}", response.statusCode());
                    return null;
                }
                return null;
            } catch (IOException e) {
                log.warn("Timeout/connection error validating agent key, attempt {}/{}: {}", attempt + 1, maxRetries, e.getMessage());
                if (attempt < maxRetries - 1) {
                    try {
                        long jitter = (long) (Math.random() * 20);
                        Thread.sleep(retryDelayMs * (1L << attempt) + jitter);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    log.error("Error validating agent key after {} attempts: {}", maxRetries, e.getMessage(), e);
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("Error validating agent key: {}", e.getMessage(), e);
                return null;
            }
        }
        return null;
    }

    /**
     * Pre-flight usage estimate for an agent key (Core {@code POST /agent-keys/estimate-usage}). Same trust model as
     * {@link #validateAgentKey(String)}: no Bearer; body carries {@code agentKey} and optional {@code agentId} /
     * {@code agentSecret}. Verifies HMAC on the raw response body when {@code X-AgentVend-Signature} and
     * {@code X-AgentVend-Timestamp} are present (200 / 403 / 429).
     *
     * @param agentKey       caller's agent API key (required)
     * @param estimatedUnits positive units to estimate (decimals allowed when the product allows)
     * @return parsed {@link com.agentvend.client.model.UsageEstimateResult} with HTTP status set from the response, or {@code null} on error / failed verification
     */
    public UsageEstimateResult estimateUsage(String agentKey, BigDecimal estimatedUnits) {
        Objects.requireNonNull(estimatedUnits, "estimatedUnits");
        if (agentKey == null || agentKey.isEmpty()) {
            log.warn("Agent key is null or empty");
            return null;
        }
        if (estimatedUnits.signum() <= 0) {
            log.warn("estimatedUnits must be positive");
            return null;
        }
        String estimateUrl = coreServiceUrl + "/agent-keys/estimate-usage";
        EstimateUsageRequest request = new EstimateUsageRequest(agentKey, agentId, agentSecret, estimatedUnits);
        int maxRetries = 5;
        long retryDelayMs = 200;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String bodyJson = objectMapper.writeValueAsString(request);
                HttpResponse<String> response = HttpSupport.postJson(httpClient, estimateUrl, bodyJson, Map.of());

                int code = response.statusCode();
                String responseText = response.body() != null ? response.body() : "";

                if (code == 200 || code == 403 || code == 429) {
                    if (responseText.isEmpty()) {
                        log.warn("Empty estimate response body for status {}", code);
                        return null;
                    }
                    String signature =
                            response.headers().firstValue(AgentVendHeaders.SIGNATURE).orElse(null);
                    String timestampStr =
                            response.headers().firstValue(AgentVendHeaders.TIMESTAMP).orElse(null);
                    if (signature != null && timestampStr != null) {
                        if (!HmacUtils.validateHmacSignature(signature, responseText + timestampStr, agentSecret)) {
                            log.warn("Invalid HMAC signature in estimate-usage response");
                            return null;
                        }
                    } else if (code == 200) {
                        log.warn("Missing HMAC signature or timestamp in estimate-usage response");
                        return null;
                    } else {
                        log.warn("Missing HMAC signature or timestamp in estimate-usage response (status {})", code);
                        return null;
                    }
                    UsageEstimateResult parsed = objectMapper.readValue(responseText, UsageEstimateResult.class);
                    parsed.setHttpStatus(code);
                    return parsed;
                }
                if (code >= 400 && code < 500) {
                    log.warn("HTTP client error estimate-usage: {}", code);
                    return null;
                }
                return null;
            } catch (IOException e) {
                log.warn("Timeout/connection error estimate-usage, attempt {}/{}: {}", attempt + 1, maxRetries, e.getMessage());
                if (attempt < maxRetries - 1) {
                    try {
                        long jitter = (long) (Math.random() * 20);
                        Thread.sleep(retryDelayMs * (1L << attempt) + jitter);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    log.error("Error estimate-usage after {} attempts: {}", maxRetries, e.getMessage(), e);
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("Error estimate-usage: {}", e.getMessage(), e);
                return null;
            }
        }
        return null;
    }

    /**
     * Core JWT usage estimate ({@code POST …/billing/usage/estimate}). Response is not HMAC-signed (see platform spec §2.2).
     *
     * @param bearerToken    {@code Authorization: Bearer} value (JWT)
     * @param userId         internal Core user id (UUID string)
     * @param agentId        agent id (UUID string)
     * @param estimatedUnits positive units to estimate
     * @return parsed estimate on 200/403/429 with JSON body, otherwise {@code null}
     */
    public UsageEstimateResult estimateUsageWithJwt(
            String bearerToken, String userId, String agentId, BigDecimal estimatedUnits) {
        Objects.requireNonNull(estimatedUnits, "estimatedUnits");
        if (bearerToken == null || bearerToken.isBlank()) {
            log.warn("bearerToken is null or empty");
            return null;
        }
        if (userId == null || userId.isBlank() || agentId == null || agentId.isBlank()) {
            log.warn("userId and agentId are required for JWT usage estimate");
            return null;
        }
        if (estimatedUnits.signum() <= 0) {
            log.warn("estimatedUnits must be positive");
            return null;
        }
        String url = coreServiceUrl + "/billing/usage/estimate";
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("userId", userId);
            bodyMap.put("agentId", agentId);
            bodyMap.put("estimatedUnits", estimatedUnits);
            String bodyJson = objectMapper.writeValueAsString(bodyMap);
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + bearerToken.trim());
            headers.put("Content-Type", "application/json; charset=UTF-8");
            HttpResponse<String> response = HttpSupport.send(httpClient, "POST", url, bodyJson, headers);
            int code = response.statusCode();
            String responseText = response.body() != null ? response.body() : "";
            if (code == 200 || code == 403 || code == 429) {
                if (responseText.isBlank()) {
                    return null;
                }
                UsageEstimateResult parsed = objectMapper.readValue(responseText, UsageEstimateResult.class);
                parsed.setHttpStatus(code);
                return parsed;
            }
            log.warn("HTTP client error billing usage estimate: {}", code);
            return null;
        } catch (IOException e) {
            log.error("Error billing usage estimate: {}", e.getMessage(), e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.error("Error billing usage estimate: {}", e.getMessage(), e);
            return null;
        }
    }

    public void clearCache() {
        cache.clear();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ValidateRequest {
        private String agentKey;
        private String agentId;
        private String agentSecret;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class EstimateUsageRequest {
        private String agentKey;
        private String agentId;
        private String agentSecret;
        private BigDecimal estimatedUnits;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ValidationResponse {
        private boolean valid;
        /** Core row id for the validated key; absent on older responses. */
        private UUID agentKeyId;
        private String userId;
        private String agentId;
        private String plan;
        private List<String> roles;
        /** Absent when {@code validationSchemaVersion} is 2 (see docs/sdk-api-spec.md §2.1). */
        private BigDecimal quotaRemaining;
        private boolean subscriptionActive;
        private String billingModelType;
        private String measurementType;
        private String unitLabel;
        private long timestamp;
        private String error;
        /** When {@code 2}, signed body omits {@code quotaRemaining}. */
        private Integer validationSchemaVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentKeyValidationResult {
        private String userId;
        private String agentId;
        private UUID agentKeyId;
        private String plan;
        private List<String> roles;
        private BigDecimal quotaRemaining;
        private boolean subscriptionActive;
        private String billingModelType;
        private String measurementType;
        private String unitLabel;
    }

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
