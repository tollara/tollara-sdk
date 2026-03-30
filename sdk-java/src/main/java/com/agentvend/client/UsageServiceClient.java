package com.agentvend.client;

import com.agentvend.client.model.CompletionStatus;
import com.agentvend.client.model.UsageReportRequest;
import com.agentvend.client.model.UsageReportResponse;
import com.agentvend.common.util.HmacUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client for the usage service: progress, completion, and usage reporting.
 */
@Slf4j
public class UsageServiceClient {

    static final String DEFAULT_USAGE_PATH_PREFIX = "/api/usage";

    private final String usageServiceUrl;
    private final String usagePathPrefix;
    private final String agentSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Same as {@link #UsageServiceClient(String, String, String, HttpClient)} with usage path prefix {@code /api/usage}.
     */
    public UsageServiceClient(String usageServiceUrl, String agentSecret, HttpClient httpClient) {
        this(usageServiceUrl, DEFAULT_USAGE_PATH_PREFIX, agentSecret, httpClient);
    }

    /**
     * @param usageServiceUrl origin for the usage service (scheme + host [+ port], no trailing slash required)
     * @param usagePathPrefix   e.g. {@code /api/usage} (default) or {@code /usage/api/v1} (ECS); must match deployment
     */
    public UsageServiceClient(String usageServiceUrl, String usagePathPrefix, String agentSecret, HttpClient httpClient) {
        this.usageServiceUrl = usageServiceUrl != null ? AgentVendUrls.trimTrailingSlashes(usageServiceUrl) : "";
        this.usagePathPrefix =
                (usagePathPrefix == null || usagePathPrefix.isEmpty())
                        ? DEFAULT_USAGE_PATH_PREFIX
                        : normalizeUsagePrefix(usagePathPrefix);
        this.agentSecret = agentSecret;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        this.objectMapper.registerModule(module);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    private static String normalizeUsagePrefix(String p) {
        String x = p.trim();
        if (!x.startsWith("/")) {
            x = "/" + x;
        }
        return AgentVendUrls.trimTrailingSlashes(x);
    }

    /**
     * Sends a progress update to the usage service (no error message).
     */
    public boolean sendProgressUpdate(String progressUrl, String requestId, String stage, int percentageComplete) {
        return sendProgressUpdate(progressUrl, requestId, stage, percentageComplete, null);
    }

    /**
     * Sends a progress update to the usage service.
     */
    public boolean sendProgressUpdate(String progressUrl, String requestId, String stage, int percentageComplete, String errorMessage) {
        if (progressUrl == null || progressUrl.isEmpty()) {
            log.warn("Progress URL is null or empty");
            return false;
        }
        try {
            String signature = null;
            String timestamp = null;
            String baseUrl = progressUrl;
            if (progressUrl.contains("?")) {
                baseUrl = progressUrl.substring(0, progressUrl.indexOf("?"));
                String queryString = progressUrl.substring(progressUrl.indexOf("?") + 1);
                for (String param : queryString.split("&")) {
                    String[] keyValue = param.split("=", 2);
                    if (keyValue.length == 2) {
                        if ("signature".equals(keyValue[0])) {
                            signature = java.net.URLDecoder.decode(keyValue[1], java.nio.charset.StandardCharsets.UTF_8);
                        } else if ("timestamp".equals(keyValue[0])) {
                            timestamp = keyValue[1];
                        }
                    }
                }
            }
            if (signature == null || timestamp == null) {
                log.warn("Missing signature or timestamp in progress URL");
                return false;
            }
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("stage", stage);
            requestBody.put("percentageComplete", percentageComplete);
            if (errorMessage != null) {
                requestBody.put("errorMessage", errorMessage);
            }
            requestBody.put("timestamp", Instant.now().toString());
            String progressUpdatePayload = objectMapper.writeValueAsString(requestBody);
            long timestampLong = Long.parseLong(timestamp);
            String newSignature = HmacUtils.calculateHmacWithTimestamp(progressUpdatePayload, timestampLong, agentSecret);
            Map<String, String> headers = Map.of(
                    AgentVendHeaders.SIGNATURE, newSignature,
                    AgentVendHeaders.TIMESTAMP, timestamp);
            HttpResponse<String> response = HttpSupport.postJson(httpClient, baseUrl, progressUpdatePayload, headers);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException e) {
            log.error("Error sending progress update: {}", e.getMessage(), e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.error("Error generating signature for progress update: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends completion with status and billed units only.
     */
    public boolean sendCompletion(String callbackUrl, String requestId, CompletionStatus status, BigDecimal units) {
        return sendCompletion(callbackUrl, requestId, status, null, null, null, units);
    }

    /**
     * Sends completion with inline result text.
     */
    public boolean sendCompletion(String callbackUrl, String requestId, CompletionStatus status, String result,
                                  BigDecimal units) {
        return sendCompletion(callbackUrl, requestId, status, result, null, null, units);
    }

    /**
     * Sends completion with result URL and content type.
     */
    public boolean sendCompletion(String callbackUrl, String requestId, CompletionStatus status, String result,
                                  String resultUrl, String contentType, BigDecimal units) {
        if (callbackUrl == null || callbackUrl.isEmpty()) {
            log.warn("Callback URL is null or empty");
            return false;
        }
        try {
            String signature = null;
            String timestamp = null;
            String baseUrl = callbackUrl;
            if (callbackUrl.contains("?")) {
                baseUrl = callbackUrl.substring(0, callbackUrl.indexOf("?"));
                String queryString = callbackUrl.substring(callbackUrl.indexOf("?") + 1);
                for (String param : queryString.split("&")) {
                    String[] keyValue = param.split("=", 2);
                    if (keyValue.length == 2) {
                        if ("signature".equals(keyValue[0])) {
                            signature = java.net.URLDecoder.decode(keyValue[1], java.nio.charset.StandardCharsets.UTF_8);
                        } else if ("timestamp".equals(keyValue[0])) {
                            timestamp = keyValue[1];
                        }
                    }
                }
            }
            if (signature == null || timestamp == null) {
                log.warn("Missing signature or timestamp in callback URL");
                return false;
            }
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("status", status.getApiValue());
            if (result != null) requestBody.put("result", result);
            if (resultUrl != null) requestBody.put("resultUrl", resultUrl);
            if (contentType != null) requestBody.put("contentType", contentType);
            requestBody.put("timestamp", Instant.now().toString());
            requestBody.put("units", units != null ? units : BigDecimal.ZERO);
            String completionPayload = objectMapper.writeValueAsString(requestBody);
            long timestampLong = Long.parseLong(timestamp);
            String newSignature = HmacUtils.calculateHmacWithTimestamp(completionPayload, timestampLong, agentSecret);
            Map<String, String> headers = Map.of(
                    AgentVendHeaders.SIGNATURE, newSignature,
                    AgentVendHeaders.TIMESTAMP, timestamp);
            HttpResponse<String> response = HttpSupport.postJson(httpClient, baseUrl, completionPayload, headers);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException e) {
            log.error("Error sending completion notification: {}", e.getMessage(), e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.error("Error generating signature for completion: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Reports usage with current time as timestamp.
     */
    public UsageReportResponse reportUsage(String userId, String agentId, BigDecimal unitsUsed) {
        return reportUsage(userId, agentId, unitsUsed, Instant.now());
    }

    /**
     * Reports usage to the usage service.
     */
    public UsageReportResponse reportUsage(String userId, String agentId, BigDecimal unitsUsed, Instant timestamp) {
        if (userId == null || agentId == null || unitsUsed == null) {
            throw new IllegalArgumentException("userId, agentId, and unitsUsed must not be null");
        }
        try {
            Instant usageTimestamp = timestamp != null ? timestamp : Instant.now();
            UsageReportRequest request = UsageReportRequest.builder()
                    .userId(userId)
                    .agentId(agentId)
                    .unitsUsed(unitsUsed)
                    .timestamp(usageTimestamp)
                    .build();
            String requestBody = objectMapper.writeValueAsString(request);
            long timestampLong = usageTimestamp.toEpochMilli();
            String signature = HmacUtils.calculateHmacWithTimestamp(requestBody, timestampLong, agentSecret);
            Map<String, String> headers = Map.of(
                    AgentVendHeaders.SIGNATURE, signature,
                    AgentVendHeaders.TIMESTAMP, String.valueOf(timestampLong));
            if (usageServiceUrl == null || usageServiceUrl.isEmpty()) {
                throw new IllegalArgumentException("usageServiceUrl must not be null or empty");
            }
            String url = AgentVendUrls.join(AgentVendUrls.join(usageServiceUrl, usagePathPrefix), "/report");
            HttpResponse<String> response = HttpSupport.postJson(httpClient, url, requestBody, headers);
            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null) {
                return objectMapper.readValue(response.body(), UsageReportResponse.class);
            }
            throw new AgentVendHttpException(response.statusCode(), "Usage report failed with status: " + response.statusCode());
        } catch (IOException e) {
            log.error("Error reporting usage: {}", e.getMessage(), e);
            throw new AgentVendHttpException("Usage report failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentVendHttpException("Usage report interrupted", e);
        } catch (AgentVendHttpException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generating signature or serializing usage report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to report usage: " + e.getMessage(), e);
        }
    }
}
