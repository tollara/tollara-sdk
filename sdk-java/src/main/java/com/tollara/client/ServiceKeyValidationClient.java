package com.tollara.client;

import com.tollara.client.model.UsageEstimateResult;
import com.tollara.common.util.HmacUtils;
import com.fasterxml.jackson.databind.JsonNode;
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
 * Client for validating service keys via the core-service validation endpoint.
 */
@Slf4j
public class ServiceKeyValidationClient {

    private final String coreServiceUrl;
    private final String serviceId;
    private final String serviceSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedValidationResult> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000;

    public ServiceKeyValidationClient(String coreServiceUrl, String serviceId, String serviceSecret, HttpClient httpClient) {
        this.coreServiceUrl = coreServiceUrl;
        this.serviceId = serviceId;
        this.serviceSecret = serviceSecret;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Validates a service key and returns user context. Uses caching (TTL 60s).
     */
    public ServiceKeyValidationResult validateServiceKey(String serviceKey) {
        if (serviceKey == null || serviceKey.isEmpty()) {
            log.warn("Service key is null or empty");
            return null;
        }
        CachedValidationResult cached = cache.get(serviceKey);
        if (cached != null && !cached.isExpired()) {
            return cached.getResult();
        }
        String validateUrl = coreServiceUrl + "/service-keys/validate";
        ValidateRequest request = new ValidateRequest(serviceKey, serviceId, serviceSecret);
        int maxRetries = 5;
        long retryDelayMs = 200;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String bodyJson = objectMapper.writeValueAsString(request);
                HttpResponse<String> response = HttpSupport.postJson(httpClient, validateUrl, bodyJson, Map.of());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String responseText = response.body();
                    String signature =
                            response.headers().firstValue(TollaraHeaders.SIGNATURE).orElse(null);
                    String timestampStr =
                            response.headers().firstValue(TollaraHeaders.TIMESTAMP).orElse(null);
                    if (signature == null || timestampStr == null) {
                        log.warn("Missing HMAC signature or timestamp in validation response");
                        return null;
                    }
                    long timestamp = Long.parseLong(timestampStr);
                    if (!HmacUtils.validateHmacSignature(signature, responseText + timestamp, serviceSecret)) {
                        log.warn("Invalid HMAC signature in validation response");
                        return null;
                    }
                    ValidationResponse validationResponse =
                            objectMapper.readValue(responseText, ValidationResponse.class);
                    if (!validationResponse.isValid()) {
                        log.warn("Service key validation failed: {}", validationResponse.getError());
                        return null;
                    }
                    String resultServiceId = validationResponse.getServiceId() != null
                            ? validationResponse.getServiceId() : serviceId;
                    ServiceKeyValidationResult result = ServiceKeyValidationResult.builder()
                            .userId(validationResponse.getUserId())
                            .serviceId(resultServiceId)
                            .serviceKeyId(validationResponse.getServiceKeyId())
                            .serviceProductId(validationResponse.getServiceProductId())
                            .roles(validationResponse.getRoles() != null ? validationResponse.getRoles() : Collections.emptyList())
                            .subscriptionStatus(validationResponse.getSubscriptionStatus())
                            .validationSchemaVersion(validationResponse.getValidationSchemaVersion())
                            .billingModelType(validationResponse.getBillingModelType())
                            .measurementType(validationResponse.getMeasurementType())
                            .unitLabel(validationResponse.getUnitLabel())
                            .build();
                    cache.put(serviceKey, new CachedValidationResult(result, System.currentTimeMillis()));
                    return result;
                }
                if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    log.warn("HTTP client error validating service key: {}", response.statusCode());
                    return null;
                }
                return null;
            } catch (IOException e) {
                log.warn("Timeout/connection error validating service key, attempt {}/{}: {}", attempt + 1, maxRetries, e.getMessage());
                if (attempt < maxRetries - 1) {
                    try {
                        long jitter = (long) (Math.random() * 20);
                        Thread.sleep(retryDelayMs * (1L << attempt) + jitter);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    log.error("Error validating service key after {} attempts: {}", maxRetries, e.getMessage(), e);
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("Error validating service key: {}", e.getMessage(), e);
                return null;
            }
        }
        return null;
    }

    /**
     * Validates a service key and returns a structured outcome (§2.1.1). Single HTTP attempt; no cache.
     */
    public ServiceKeyValidationOutcome validateServiceKeyWithOutcome(String serviceKey) {
        if (serviceKey == null || serviceKey.isBlank()) {
            return ServiceKeyValidationOutcome.failure(
                    ValidationFailureCode.MISSING_KEY, null, null);
        }
        String validateUrl = coreServiceUrl + "/service-keys/validate";
        ValidateRequest request = new ValidateRequest(serviceKey, serviceId, serviceSecret);
        try {
            String bodyJson = objectMapper.writeValueAsString(request);
            HttpResponse<String> response = HttpSupport.postJson(httpClient, validateUrl, bodyJson, Map.of());
            int httpStatus = response.statusCode();
            String responseText = response.body();
            if (httpStatus < 200 || httpStatus >= 300) {
                ServiceKeyValidationOutcome unsignedInvalid =
                        tryInvalidKeyFromUnsignedErrorBody(responseText, httpStatus);
                if (unsignedInvalid != null) {
                    return unsignedInvalid;
                }
                return ServiceKeyValidationOutcome.failure(
                        ValidationFailureCode.HTTP_ERROR, null, httpStatus);
            }
            String signature = response.headers().firstValue(TollaraHeaders.SIGNATURE).orElse(null);
            String timestampStr = response.headers().firstValue(TollaraHeaders.TIMESTAMP).orElse(null);
            if (signature == null || timestampStr == null) {
                return ServiceKeyValidationOutcome.failure(
                        ValidationFailureCode.MISSING_SIGNATURE_HEADERS, null, httpStatus);
            }
            long timestamp = Long.parseLong(timestampStr);
            if (!HmacUtils.validateHmacSignature(signature, responseText + timestamp, serviceSecret)) {
                return ServiceKeyValidationOutcome.failure(
                        ValidationFailureCode.HMAC_MISMATCH, null, httpStatus);
            }
            ValidationResponse validationResponse;
            try {
                validationResponse = objectMapper.readValue(responseText, ValidationResponse.class);
            } catch (IOException e) {
                return ServiceKeyValidationOutcome.failure(
                        ValidationFailureCode.PARSE_ERROR, null, httpStatus);
            }
            if (!validationResponse.isValid()) {
                return ServiceKeyValidationOutcome.failure(
                        ValidationFailureCode.INVALID_KEY, validationResponse.getError(), httpStatus);
            }
            String resultServiceId = validationResponse.getServiceId() != null
                    ? validationResponse.getServiceId() : serviceId;
            ServiceKeyValidationResult result = ServiceKeyValidationResult.builder()
                    .userId(validationResponse.getUserId())
                    .serviceId(resultServiceId)
                    .serviceKeyId(validationResponse.getServiceKeyId())
                    .serviceProductId(validationResponse.getServiceProductId())
                    .roles(validationResponse.getRoles() != null ? validationResponse.getRoles() : Collections.emptyList())
                    .subscriptionStatus(validationResponse.getSubscriptionStatus())
                    .validationSchemaVersion(validationResponse.getValidationSchemaVersion())
                    .billingModelType(validationResponse.getBillingModelType())
                    .measurementType(validationResponse.getMeasurementType())
                    .unitLabel(validationResponse.getUnitLabel())
                    .build();
            return ServiceKeyValidationOutcome.success(result);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ServiceKeyValidationOutcome.failure(ValidationFailureCode.NETWORK, null, null);
        } catch (Exception e) {
            log.error("Error validating service key with outcome: {}", e.getMessage(), e);
            return ServiceKeyValidationOutcome.failure(ValidationFailureCode.NETWORK, null, null);
        }
    }

    /**
     * Pre-flight usage estimate for a service key (Core {@code POST /service-keys/estimate-usage}). Same trust model as
     * {@link #validateServiceKey(String)}: no Bearer; body carries {@code serviceKey} and optional {@code serviceId} /
     * {@code serviceSecret}. Verifies HMAC on the raw response body when {@code X-Tollara-Signature} and
     * {@code X-Tollara-Timestamp} are present (200 / 403 / 429).
     *
     * @param serviceKey     caller's service API key (required)
     * @param estimatedUnits positive units to estimate (decimals allowed when the product allows)
     * @return parsed {@link com.tollara.client.model.UsageEstimateResult} with HTTP status set from the response, or {@code null} on error / failed verification
     */
    public UsageEstimateResult estimateUsage(String serviceKey, BigDecimal estimatedUnits) {
        Objects.requireNonNull(estimatedUnits, "estimatedUnits");
        if (serviceKey == null || serviceKey.isEmpty()) {
            log.warn("Service key is null or empty");
            return null;
        }
        if (estimatedUnits.signum() <= 0) {
            log.warn("estimatedUnits must be positive");
            return null;
        }
        String estimateUrl = coreServiceUrl + "/service-keys/estimate-usage";
        EstimateUsageRequest request = new EstimateUsageRequest(serviceKey, serviceId, serviceSecret, estimatedUnits);
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
                            response.headers().firstValue(TollaraHeaders.SIGNATURE).orElse(null);
                    String timestampStr =
                            response.headers().firstValue(TollaraHeaders.TIMESTAMP).orElse(null);
                    if (signature != null && timestampStr != null) {
                        if (!HmacUtils.validateHmacSignature(signature, responseText + timestampStr, serviceSecret)) {
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
     * @param serviceId      service id (UUID string)
     * @param estimatedUnits positive units to estimate
     * @return parsed estimate on 200/403/429 with JSON body, otherwise {@code null}
     */
    public UsageEstimateResult estimateUsageWithJwt(
            String bearerToken, String userId, String serviceId, BigDecimal estimatedUnits) {
        Objects.requireNonNull(estimatedUnits, "estimatedUnits");
        if (bearerToken == null || bearerToken.isBlank()) {
            log.warn("bearerToken is null or empty");
            return null;
        }
        if (userId == null || userId.isBlank() || serviceId == null || serviceId.isBlank()) {
            log.warn("userId and serviceId are required for JWT usage estimate");
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
            bodyMap.put("serviceId", serviceId);
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
        private String serviceKey;
        private String serviceId;
        private String serviceSecret;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class EstimateUsageRequest {
        private String serviceKey;
        private String serviceId;
        private String serviceSecret;
        private BigDecimal estimatedUnits;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ValidationResponse {
        private boolean valid;
        /** Core row id for the validated key; absent on older responses. */
        private UUID serviceKeyId;
        private String userId;
        private String serviceId;
        private String serviceProductId;
        private List<String> roles;
        private String subscriptionStatus;
        private String billingModelType;
        private String measurementType;
        private String unitLabel;
        private long timestamp;
        private String error;
        private Integer validationSchemaVersion;
    }

    /** Unsigned 401/403 from Core: {@code { "valid": false, "error"?: string }}. */
    private ServiceKeyValidationOutcome tryInvalidKeyFromUnsignedErrorBody(String responseText, int httpStatus) {
        if (httpStatus != 401 && httpStatus != 403) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(responseText);
            if (node.has("valid") && !node.get("valid").asBoolean()) {
                String error = node.has("error") && !node.get("error").isNull()
                        ? node.get("error").asText()
                        : null;
                return ServiceKeyValidationOutcome.failure(
                        ValidationFailureCode.INVALID_KEY, error, httpStatus);
            }
        } catch (IOException ignored) {
            // not JSON
        }
        return null;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceKeyValidationResult {
        private String userId;
        private String serviceId;
        private UUID serviceKeyId;
        private String serviceProductId;
        private List<String> roles;
        private String subscriptionStatus;
        private Integer validationSchemaVersion;
        private String billingModelType;
        private String measurementType;
        private String unitLabel;

        /**
         * Returns {@code true} when {@link #subscriptionStatus} is invoke-eligible.
         */
        public boolean grantAccess() {
            return TollaraRequestVerifier.grantAccess(subscriptionStatus);
        }

        /** Same rule as {@link #grantAccess()}. */
        public static boolean grantAccess(String subscriptionStatus) {
            return TollaraRequestVerifier.grantAccess(subscriptionStatus);
        }
    }

    /** Canonical failure codes for validate outcome (§2.1.1). */
    public enum ValidationFailureCode {
        MISSING_KEY,
        NETWORK,
        HTTP_ERROR,
        MISSING_SIGNATURE_HEADERS,
        HMAC_MISMATCH,
        INVALID_KEY,
        PARSE_ERROR
    }

    @Data
    @AllArgsConstructor
    public static class ServiceKeyValidationFailure {
        private ValidationFailureCode code;
        private String message;
        private Integer httpStatus;
    }

    public static final class ServiceKeyValidationOutcome {
        private final boolean ok;
        private final ServiceKeyValidationResult result;
        private final ServiceKeyValidationFailure failure;

        private ServiceKeyValidationOutcome(
                boolean ok, ServiceKeyValidationResult result, ServiceKeyValidationFailure failure) {
            this.ok = ok;
            this.result = result;
            this.failure = failure;
        }

        public static ServiceKeyValidationOutcome success(ServiceKeyValidationResult result) {
            return new ServiceKeyValidationOutcome(true, result, null);
        }

        public static ServiceKeyValidationOutcome failure(
                ValidationFailureCode code, String message, Integer httpStatus) {
            return new ServiceKeyValidationOutcome(
                    false, null, new ServiceKeyValidationFailure(code, message, httpStatus));
        }

        public boolean isOk() {
            return ok;
        }

        public ServiceKeyValidationResult getResult() {
            return result;
        }

        public ServiceKeyValidationFailure getFailure() {
            return failure;
        }
    }

    private static class CachedValidationResult {
        private final ServiceKeyValidationResult result;
        private final long timestamp;

        CachedValidationResult(ServiceKeyValidationResult result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }

        ServiceKeyValidationResult getResult() {
            return result;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}
