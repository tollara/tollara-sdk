package com.tollara.client;

import com.tollara.client.model.CompletionStatus;
import com.tollara.client.model.UsageCallbackResult;
import com.tollara.client.model.UsageReportRequest;
import com.tollara.client.model.UsageReportResponse;
import com.tollara.common.util.HmacUtils;
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
    private final String serviceSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Same as {@link #UsageServiceClient(String, String, String, HttpClient)} with usage path prefix {@code /api/usage}.
     */
    public UsageServiceClient(String usageServiceUrl, String serviceSecret, HttpClient httpClient) {
        this(usageServiceUrl, DEFAULT_USAGE_PATH_PREFIX, serviceSecret, httpClient);
    }

    /**
     * @param usageServiceUrl origin for the usage service (scheme + host [+ port], no trailing slash required)
     * @param usagePathPrefix   e.g. {@code /api/usage} (default) or {@code /usage/api/v1} (ECS); must match deployment
     */
    public UsageServiceClient(String usageServiceUrl, String usagePathPrefix, String serviceSecret, HttpClient httpClient) {
        this.usageServiceUrl = usageServiceUrl != null ? TollaraUrls.trimTrailingSlashes(usageServiceUrl) : "";
        this.usagePathPrefix =
                (usagePathPrefix == null || usagePathPrefix.isEmpty())
                        ? DEFAULT_USAGE_PATH_PREFIX
                        : normalizeUsagePrefix(usagePathPrefix);
        this.serviceSecret = serviceSecret;
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
        return TollaraUrls.trimTrailingSlashes(x);
    }

    private record ParsedCallbackUrl(String baseUrl, String timestamp) {}

    private static ParsedCallbackUrl parseCallbackUrl(String urlWithQuery) {
        if (urlWithQuery == null || urlWithQuery.isEmpty()) {
            return new ParsedCallbackUrl("", null);
        }
        String baseUrl = urlWithQuery;
        String timestamp = null;
        if (urlWithQuery.contains("?")) {
            baseUrl = urlWithQuery.substring(0, urlWithQuery.indexOf("?"));
            String queryString = urlWithQuery.substring(urlWithQuery.indexOf("?") + 1);
            for (String param : queryString.split("&")) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2 && "timestamp".equals(keyValue[0])) {
                    timestamp = keyValue[1];
                }
            }
        }
        return new ParsedCallbackUrl(baseUrl, timestamp);
    }

    private UsageCallbackResult postSignedUsageCallback(String urlWithQuery, String bodyString) {
        ParsedCallbackUrl parsed = parseCallbackUrl(urlWithQuery);
        if (parsed.timestamp() == null) {
            String statusText = urlWithQuery != null && !urlWithQuery.isEmpty()
                    ? "Missing timestamp query parameter in URL"
                    : "Missing or invalid callback/progress URL";
            return UsageCallbackResult.builder()
                    .success(false)
                    .httpStatus(0)
                    .httpStatusText(statusText)
                    .requestUrl(parsed.baseUrl())
                    .build();
        }
        try {
            long timestampLong = Long.parseLong(parsed.timestamp());
            String signature = HmacUtils.calculateHmacWithTimestamp(bodyString, timestampLong, serviceSecret);
            Map<String, String> headers = Map.of(
                    TollaraHeaders.SIGNATURE, signature,
                    TollaraHeaders.TIMESTAMP, parsed.timestamp());
            HttpResponse<String> response = HttpSupport.postJson(httpClient, parsed.baseUrl(), bodyString, headers);
            String responseBody = response.body();
            return UsageCallbackResult.builder()
                    .success(response.statusCode() >= 200 && response.statusCode() < 300)
                    .httpStatus(response.statusCode())
                    .httpStatusText(response.statusCode() >= 200 && response.statusCode() < 300 ? "OK" : "HTTP " + response.statusCode())
                    .requestUrl(parsed.baseUrl())
                    .responseBody(responseBody == null || responseBody.isEmpty() ? null : responseBody)
                    .build();
        } catch (IOException e) {
            log.error("Error posting usage callback: {}", e.getMessage(), e);
            return UsageCallbackResult.builder()
                    .success(false)
                    .httpStatus(0)
                    .httpStatusText("Network error")
                    .requestUrl(parsed.baseUrl())
                    .networkError(e.getMessage())
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return UsageCallbackResult.builder()
                    .success(false)
                    .httpStatus(0)
                    .httpStatusText("Network error")
                    .requestUrl(parsed.baseUrl())
                    .networkError(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Error generating signature for usage callback: {}", e.getMessage(), e);
            return UsageCallbackResult.builder()
                    .success(false)
                    .httpStatus(0)
                    .httpStatusText("Network error")
                    .requestUrl(parsed.baseUrl())
                    .networkError(e.getMessage())
                    .build();
        }
    }

    /**
     * Sends a progress update to the usage service (no error message).
     */
    public UsageCallbackResult sendProgressUpdate(String progressUrl, String requestId, String stage, int percentageComplete) {
        return sendProgressUpdate(progressUrl, requestId, stage, percentageComplete, null);
    }

    /**
     * Sends a progress update to the usage service.
     */
    public UsageCallbackResult sendProgressUpdate(String progressUrl, String requestId, String stage, int percentageComplete, String errorMessage) {
        if (progressUrl == null || progressUrl.isEmpty()) {
            log.warn("Progress URL is null or empty");
            return UsageCallbackResult.builder()
                    .success(false)
                    .httpStatus(0)
                    .httpStatusText("Missing or invalid callback/progress URL")
                    .requestUrl("")
                    .build();
        }
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("stage", stage);
            requestBody.put("percentageComplete", percentageComplete);
            requestBody.put("timestamp", Instant.now().toString());
            if (errorMessage != null) {
                requestBody.put("errorMessage", errorMessage);
            }
            String progressUpdatePayload = objectMapper.writeValueAsString(requestBody);
            return postSignedUsageCallback(progressUrl, progressUpdatePayload);
        } catch (IOException e) {
            log.error("Error serializing progress update: {}", e.getMessage(), e);
            return UsageCallbackResult.builder()
                    .success(false)
                    .httpStatus(0)
                    .httpStatusText("Network error")
                    .requestUrl(parseCallbackUrl(progressUrl).baseUrl())
                    .networkError(e.getMessage())
                    .build();
        }
    }

    /**
     * Sends completion with status and billed units only.
     */
    public UsageCallbackResult sendCompletion(String callbackUrl, String requestId, CompletionStatus status, BigDecimal units) {
        return sendCompletion(callbackUrl, requestId, status, null, null, null, units);
    }

    /**
     * Sends completion with inline result text.
     */
    public UsageCallbackResult sendCompletion(String callbackUrl, String requestId, CompletionStatus status, String result,
                                  BigDecimal units) {
        return sendCompletion(callbackUrl, requestId, status, result, null, null, units);
    }

    /**
     * Sends completion with result URL and content type.
     */
    public UsageCallbackResult sendCompletion(String callbackUrl, String requestId, CompletionStatus status, String result,
                                  String resultUrl, String contentType, BigDecimal units) {
        if (callbackUrl == null || callbackUrl.isEmpty()) {
            log.warn("Callback URL is null or empty");
            return UsageCallbackResult.builder()
                    .success(false)
                    .httpStatus(0)
                    .httpStatusText("Missing or invalid callback/progress URL")
                    .requestUrl("")
                    .build();
        }
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("status", status.getApiValue());
            requestBody.put("timestamp", Instant.now().toString());
            requestBody.put("units", units != null ? units : BigDecimal.ZERO);
            if (result != null) requestBody.put("result", result);
            if (resultUrl != null) requestBody.put("resultUrl", resultUrl);
            if (contentType != null) requestBody.put("contentType", contentType);
            String completionPayload = objectMapper.writeValueAsString(requestBody);
            return postSignedUsageCallback(callbackUrl, completionPayload);
        } catch (IOException e) {
            log.error("Error serializing completion: {}", e.getMessage(), e);
            return UsageCallbackResult.builder()
                    .success(false)
                    .httpStatus(0)
                    .httpStatusText("Network error")
                    .requestUrl(parseCallbackUrl(callbackUrl).baseUrl())
                    .networkError(e.getMessage())
                    .build();
        }
    }

    /**
     * Reports usage with current time as timestamp.
     */
    public UsageReportResponse reportUsage(String userId, String serviceId, BigDecimal unitsUsed) {
        return reportUsage(userId, serviceId, unitsUsed, Instant.now());
    }

    /**
     * Reports usage to the usage service.
     */
    public UsageReportResponse reportUsage(String userId, String serviceId, BigDecimal unitsUsed, Instant timestamp) {
        if (userId == null || serviceId == null || unitsUsed == null) {
            throw new IllegalArgumentException("userId, serviceId, and unitsUsed must not be null");
        }
        try {
            Instant usageTimestamp = timestamp != null ? timestamp : Instant.now();
            UsageReportRequest request = UsageReportRequest.builder()
                    .userId(userId)
                    .serviceId(serviceId)
                    .unitsUsed(unitsUsed)
                    .timestamp(usageTimestamp)
                    .build();
            String requestBody = objectMapper.writeValueAsString(request);
            long epochSeconds = usageTimestamp.getEpochSecond();
            String signature = HmacUtils.calculateHmacWithTimestamp(requestBody, epochSeconds, serviceSecret);
            Map<String, String> headers = Map.of(
                    TollaraHeaders.SIGNATURE, signature,
                    TollaraHeaders.TIMESTAMP, String.valueOf(epochSeconds));
            if (usageServiceUrl == null || usageServiceUrl.isEmpty()) {
                throw new IllegalArgumentException("usageServiceUrl must not be null or empty");
            }
            String url = TollaraUrls.join(TollaraUrls.join(usageServiceUrl, usagePathPrefix), "/report");
            HttpResponse<String> response = HttpSupport.postJson(httpClient, url, requestBody, headers);
            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null) {
                return objectMapper.readValue(response.body(), UsageReportResponse.class);
            }
            throw new TollaraHttpException(response.statusCode(), "Usage report failed with status: " + response.statusCode());
        } catch (IOException e) {
            log.error("Error reporting usage: {}", e.getMessage(), e);
            throw new TollaraHttpException("Usage report failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TollaraHttpException("Usage report interrupted", e);
        } catch (TollaraHttpException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generating signature or serializing usage report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to report usage: " + e.getMessage(), e);
        }
    }
}
