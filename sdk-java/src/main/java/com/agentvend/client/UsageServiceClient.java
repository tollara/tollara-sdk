package com.agentvend.client;

import com.agentvend.client.model.UsageReportRequest;
import com.agentvend.client.model.UsageReportResponse;
import com.agentvend.common.util.HmacUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
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

    private final String usageServiceUrl;
    private final String agentSecret;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public UsageServiceClient(String usageServiceUrl, String agentSecret, RestTemplate restTemplate) {
        this.usageServiceUrl = usageServiceUrl;
        this.agentSecret = agentSecret;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        this.objectMapper.registerModule(module);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
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
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-AgentVend-Signature", newSignature);
            headers.set("X-AgentVend-Timestamp", timestamp);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Void> response = restTemplate.exchange(baseUrl, HttpMethod.POST, entity, Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.error("Error sending progress update: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Error generating signature for progress update: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends a completion notification to the usage service.
     */
    public boolean sendCompletion(String callbackUrl, String requestId, String status, String result,
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
            requestBody.put("status", status);
            if (result != null) requestBody.put("result", result);
            if (resultUrl != null) requestBody.put("resultUrl", resultUrl);
            if (contentType != null) requestBody.put("contentType", contentType);
            requestBody.put("timestamp", Instant.now().toString());
            requestBody.put("units", units != null ? units : BigDecimal.ZERO);
            String completionPayload = objectMapper.writeValueAsString(requestBody);
            long timestampLong = Long.parseLong(timestamp);
            String newSignature = HmacUtils.calculateHmacWithTimestamp(completionPayload, timestampLong, agentSecret);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-AgentVend-Signature", newSignature);
            headers.set("X-AgentVend-Timestamp", timestamp);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Void> response = restTemplate.exchange(baseUrl, HttpMethod.POST, entity, Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.error("Error sending completion notification: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Error generating signature for completion: {}", e.getMessage(), e);
            return false;
        }
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
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-AgentVend-Signature", signature);
            headers.set("X-AgentVend-Timestamp", String.valueOf(timestampLong));
            HttpEntity<UsageReportRequest> entity = new HttpEntity<>(request, headers);
            String baseUrl = usageServiceUrl != null ? usageServiceUrl.replaceAll("/$", "") : "";
            if (baseUrl.isEmpty()) {
                throw new IllegalArgumentException("usageServiceUrl must not be null or empty");
            }
            String url = baseUrl + "/api/usage/report";
            ResponseEntity<UsageReportResponse> response = restTemplate.exchange(url, HttpMethod.POST, entity, UsageReportResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new RestClientException("Usage report failed with status: " + (response.getStatusCode()));
        } catch (RestClientException e) {
            log.error("Error reporting usage: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error generating signature or serializing usage report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to report usage: " + e.getMessage(), e);
        }
    }
}
