package com.bugisiw.marketplace.client;

import com.bugisiw.marketplace.client.model.UsageReportRequest;
import com.bugisiw.marketplace.client.model.UsageReportResponse;
import com.bugisiw.marketplace.common.util.HmacUtils;
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
 * Client for calling the usage service progress, completion, and usage reporting endpoints.
 */
@Slf4j
public class UsageServiceClient {

    private final String usageServiceUrl;
    private final String agentSecret;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new UsageServiceClient.
     *
     * @param usageServiceUrl The base URL of the usage service
     * @param agentSecret The agent's secret key for HMAC signature generation
     * @param restTemplate RestTemplate for HTTP calls
     */
    public UsageServiceClient(String usageServiceUrl, String agentSecret, RestTemplate restTemplate) {
        this.usageServiceUrl = usageServiceUrl;
        this.agentSecret = agentSecret;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        // Configure ObjectMapper to match usage-service configuration
        // This ensures LocalDateTime is serialized as ISO strings, not arrays
        JavaTimeModule module = new JavaTimeModule();
        LocalDateTimeSerializer localDateTimeSerializer = new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        module.addSerializer(LocalDateTime.class, localDateTimeSerializer);
        this.objectMapper.registerModule(module);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Exclude null fields to match usage-service behavior
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Sends a progress update to the usage service.
     *
     * @param progressUrl The full progress URL (including signature and timestamp from gateway)
     * @param requestId The request ID
     * @param stage The current processing stage
     * @param percentageComplete The percentage complete (0-100)
     * @param errorMessage Optional error message
     * @return true if the update was successful, false otherwise
     */
    public boolean sendProgressUpdate(String progressUrl, String requestId, String stage, int percentageComplete, String errorMessage) {
        if (progressUrl == null || progressUrl.isEmpty()) {
            log.warn("Progress URL is null or empty - cannot send progress update");
            return false;
        }
        
        try {
            // Extract signature and timestamp from URL query parameters
            String signature = null;
            String timestamp = null;
            String baseUrl = progressUrl;
            
            if (progressUrl.contains("?")) {
                baseUrl = progressUrl.substring(0, progressUrl.indexOf("?"));
                String queryString = progressUrl.substring(progressUrl.indexOf("?") + 1);
                String[] params = queryString.split("&");
                for (String param : params) {
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
                log.warn("Missing signature or timestamp in progress URL: {}", progressUrl);
                return false;
            }
            
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("stage", stage);
            requestBody.put("percentageComplete", percentageComplete);
            if (errorMessage != null) {
                requestBody.put("errorMessage", errorMessage);
            }
            // Serialize timestamp as ISO string to avoid RestTemplate serializing it as an array
            requestBody.put("timestamp", Instant.now().toString());

            // Generate a NEW signature for the progress update request body using the agent secret
            // The signature in the URL is for the async request body, not the progress update body
            String progressUpdatePayload = objectMapper.writeValueAsString(requestBody);
            long timestampLong = Long.parseLong(timestamp);
            String newSignature = HmacUtils.calculateHmacWithTimestamp(progressUpdatePayload, timestampLong, agentSecret);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Marketplace-Signature", newSignature);
            headers.set("X-Marketplace-Timestamp", timestamp);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("Sending progress update - requestId: {}, percentageComplete: {}", requestId, percentageComplete);

            ResponseEntity<Void> response = restTemplate.exchange(
                    baseUrl,
                    HttpMethod.POST,
                    entity,
                    Void.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Progress update sent successfully for request {}: {}% complete", 
                        requestId, percentageComplete);
                return true;
            } else {
                log.warn("Progress update returned non-2xx status: {}", response.getStatusCode());
                return false;
            }
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
     *
     * @param callbackUrl The full callback URL (including signature and timestamp from gateway)
     * @param requestId The request ID
     * @param status The job status (e.g., "COMPLETED", "FAILED")
     * @param result Optional result data
     * @param resultUrl Optional URL to the result
     * @param contentType Optional content type of the result
     * @param units Optional units used
     * @return true if the completion was successful, false otherwise
     */
    public boolean sendCompletion(String callbackUrl, String requestId, String status, String result, 
                                  String resultUrl, String contentType, BigDecimal units) {
        if (callbackUrl == null || callbackUrl.isEmpty()) {
            log.warn("Callback URL is null or empty - cannot send completion notification");
            return false;
        }
        
        try {
            // Extract signature and timestamp from URL query parameters
            String signature = null;
            String timestamp = null;
            String baseUrl = callbackUrl;
            
            if (callbackUrl.contains("?")) {
                baseUrl = callbackUrl.substring(0, callbackUrl.indexOf("?"));
                String queryString = callbackUrl.substring(callbackUrl.indexOf("?") + 1);
                String[] params = queryString.split("&");
                for (String param : params) {
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
                log.warn("Missing signature or timestamp in callback URL: {}", callbackUrl);
                return false;
            }
            
            // Use LinkedHashMap with key order matching usage-service serialization of AsyncCompletionRequest.
            // Server payload order is: status, result, contentType, timestamp, units (resultUrl omitted when null).
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("status", status);
            if (result != null) {
                requestBody.put("result", result);
            }
            if (resultUrl != null) {
                requestBody.put("resultUrl", resultUrl);
            }
            if (contentType != null) {
                requestBody.put("contentType", contentType);
            }
            requestBody.put("timestamp", Instant.now().toString());
            requestBody.put("units", units != null ? units : BigDecimal.ZERO);

            // Generate a NEW signature for the completion request body using the agent secret
            // The signature in the URL is for the async request body, not the completion body
            String completionPayload = objectMapper.writeValueAsString(requestBody);
            long timestampLong = Long.parseLong(timestamp);
            String newSignature = HmacUtils.calculateHmacWithTimestamp(completionPayload, timestampLong, agentSecret);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Marketplace-Signature", newSignature);
            headers.set("X-Marketplace-Timestamp", timestamp);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("Sending completion notification - requestId: {}, status: {}", requestId, status);

            ResponseEntity<Void> response = restTemplate.exchange(
                    baseUrl,
                    HttpMethod.POST,
                    entity,
                    Void.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Completion notification sent successfully for request {}: {}", 
                        requestId, status);
                return true;
            } else {
                log.warn("Completion notification returned non-2xx status: {}", response.getStatusCode());
                return false;
            }
        } catch (RestClientException e) {
            log.error("Error sending completion notification: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Error generating signature for completion: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Reports usage to the usage service for non-proxied agents.
     * This method is called by non-proxied agents after processing a request to report usage.
     *
     * @param userId The user ID
     * @param agentId The agent ID
     * @param unitsUsed The units used (typically 1.0 for per-request billing)
     * @param timestamp The timestamp of the usage (null for current time)
     * @return UsageReportResponse containing status, overLimit flag, and remaining limits
     * @throws RestClientException if the HTTP request fails
     */
    public UsageReportResponse reportUsage(String userId, String agentId, BigDecimal unitsUsed, Instant timestamp) {
        if (userId == null || agentId == null || unitsUsed == null) {
            log.error("Cannot report usage - userId, agentId, or unitsUsed is null");
            throw new IllegalArgumentException("userId, agentId, and unitsUsed must not be null");
        }

        try {
            // Use current time if timestamp is null
            Instant usageTimestamp = timestamp != null ? timestamp : Instant.now();
            
            // Create usage report request
            UsageReportRequest request = UsageReportRequest.builder()
                    .userId(userId)
                    .agentId(agentId)
                    .unitsUsed(unitsUsed)
                    .timestamp(usageTimestamp)
                    .build();

            // Serialize request body
            String requestBody = objectMapper.writeValueAsString(request);
            
            // Generate HMAC signature
            long timestampLong = usageTimestamp.toEpochMilli();
            log.info(">>>>> DEBUG UsageServiceClient.reportUsage - agentSecret length: {}, agentSecret (first 20 chars): {}, requestBody: {}, timestamp: {}", 
                    agentSecret != null ? agentSecret.length() : 0, 
                    agentSecret != null && agentSecret.length() > 20 ? agentSecret.substring(0, 20) : agentSecret,
                    requestBody, timestampLong);
            String signature = HmacUtils.calculateHmacWithTimestamp(requestBody, timestampLong, agentSecret);
            log.info(">>>>> DEBUG UsageServiceClient.reportUsage - generated signature: {}", signature);

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Marketplace-Signature", signature);
            headers.set("X-Marketplace-Timestamp", String.valueOf(timestampLong));

            HttpEntity<UsageReportRequest> entity = new HttpEntity<>(request, headers);

            log.debug("Reporting usage - userId: {}, agentId: {}, unitsUsed: {}", userId, agentId, unitsUsed);

            // Make POST request to /api/usage/report
            String baseUrl = usageServiceUrl != null ? usageServiceUrl.replaceAll("/$", "") : "";
            if (baseUrl.isEmpty()) {
                throw new IllegalArgumentException("usageServiceUrl must not be null or empty");
            }
            String url = baseUrl + "/api/usage/report";
            ResponseEntity<UsageReportResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    UsageReportResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                UsageReportResponse reportResponse = response.getBody();
                if (reportResponse != null) {
                    log.debug("Usage reported successfully - status: {}, overLimit: {}", 
                            reportResponse.getStatus(), reportResponse.isOverLimit());
                    return reportResponse;
                } else {
                    log.warn("Usage report returned 2xx but body is null");
                    throw new RestClientException("Usage report returned null response body");
                }
            } else {
                log.warn("Usage report returned non-2xx status: {}", response.getStatusCode());
                throw new RestClientException("Usage report failed with status: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("Error reporting usage: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error generating signature or serializing usage report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to report usage: " + e.getMessage(), e);
        }
    }
}

