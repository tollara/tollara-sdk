package com.agentvend.client;

import com.agentvend.client.model.InboundHmacRequest;
import com.agentvend.client.model.SignedUserContext;
import com.agentvend.common.util.HmacUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Verifies AgentVend requests by validating HMAC signatures.
 * Extracts user context from X-AgentVend-* headers.
 */
@Slf4j
public class AgentvendRequestVerifier {

    private final String agentSecret;
    private final ObjectMapper objectMapper;

    public AgentvendRequestVerifier(String agentSecret) {
        this.agentSecret = agentSecret;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Verifies inbound HMAC using a single request object (preferred).
     */
    public boolean verifyInboundHmac(InboundHmacRequest request) {
        if (request == null || request.getSignedUserContext() == null) {
            log.warn("Missing inbound HMAC request or signed user context");
            return false;
        }
        SignedUserContext s = request.getSignedUserContext();
        return verifyHmacSignature(
                request.getSignature(),
                request.getTimestamp(),
                request.getPayload(),
                s.getUserId(),
                s.getPlan(),
                s.getRoles(),
                s.getQuotaRemaining());
    }

    /**
     * Verifies inbound HMAC from Spring {@link HttpHeaders} (case-insensitive) and raw body.
     */
    public boolean verifyInboundHmac(HttpHeaders headers, String payload) {
        if (headers == null) {
            return false;
        }
        return verifyInboundHmac(
                headers.getFirst(AgentVendHeaders.SIGNATURE),
                headers.getFirst(AgentVendHeaders.TIMESTAMP),
                payload,
                headers);
    }

    /**
     * Verifies inbound HMAC from a header map and raw body. Header names are matched case-insensitively.
     */
    public boolean verifyInboundHmac(Map<String, String> headers, String payload) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        return verifyInboundHmac(
                getHeaderIgnoreCase(headers, AgentVendHeaders.SIGNATURE),
                getHeaderIgnoreCase(headers, AgentVendHeaders.TIMESTAMP),
                payload,
                headers);
    }

    private boolean verifyInboundHmac(
            String signature, String timestamp, String payload, HttpHeaders headers) {
        SignedUserContext signed =
                parseSignedUserContext(headers.getFirst(AgentVendHeaders.USER_ID),
                        headers.getFirst(AgentVendHeaders.PLAN),
                        headers.getFirst(AgentVendHeaders.ROLES),
                        headers.getFirst(AgentVendHeaders.QUOTA_REMAINING));
        return verifyInboundHmac(InboundHmacRequest.builder()
                .signature(signature)
                .timestamp(timestamp)
                .payload(payload)
                .signedUserContext(signed)
                .build());
    }

    private boolean verifyInboundHmac(
            String signature, String timestamp, String payload, Map<String, String> headers) {
        SignedUserContext signed =
                parseSignedUserContext(
                        getHeaderIgnoreCase(headers, AgentVendHeaders.USER_ID),
                        getHeaderIgnoreCase(headers, AgentVendHeaders.PLAN),
                        getHeaderIgnoreCase(headers, AgentVendHeaders.ROLES),
                        getHeaderIgnoreCase(headers, AgentVendHeaders.QUOTA_REMAINING));
        return verifyInboundHmac(InboundHmacRequest.builder()
                .signature(signature)
                .timestamp(timestamp)
                .payload(payload)
                .signedUserContext(signed)
                .build());
    }

    private static SignedUserContext parseSignedUserContext(
            String userId, String plan, String rolesHeader, String quotaHeader) {
        List<String> roles = parseRolesList(rolesHeader);
        BigDecimal quota = parseQuota(quotaHeader);
        return SignedUserContext.builder().userId(userId).plan(plan).roles(roles).quotaRemaining(quota).build();
    }

    /**
     * Parses full user context from Spring {@link HttpHeaders}, including unsigned fields.
     */
    public UserContext userContextFromHeaders(HttpHeaders headers) {
        if (headers == null) {
            return UserContext.builder()
                    .roles(Collections.emptyList())
                    .subscriptionActive(false)
                    .build();
        }
        return extractUserContext(
                headers.getFirst(AgentVendHeaders.USER_ID),
                headers.getFirst(AgentVendHeaders.PLAN),
                headers.getFirst(AgentVendHeaders.ROLES),
                headers.getFirst(AgentVendHeaders.QUOTA_REMAINING),
                headers.getFirst(AgentVendHeaders.SUBSCRIPTION_ACTIVE));
    }

    /**
     * Parses full user context from a header map (case-insensitive keys).
     */
    public UserContext userContextFromHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return UserContext.builder()
                    .roles(Collections.emptyList())
                    .subscriptionActive(false)
                    .build();
        }
        return extractUserContext(
                getHeaderIgnoreCase(headers, AgentVendHeaders.USER_ID),
                getHeaderIgnoreCase(headers, AgentVendHeaders.PLAN),
                getHeaderIgnoreCase(headers, AgentVendHeaders.ROLES),
                getHeaderIgnoreCase(headers, AgentVendHeaders.QUOTA_REMAINING),
                getHeaderIgnoreCase(headers, AgentVendHeaders.SUBSCRIPTION_ACTIVE));
    }

    private static String getHeaderIgnoreCase(Map<String, String> headers, String canonicalName) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(canonicalName)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Verifies the HMAC signature. Prefer {@link #verifyInboundHmac(InboundHmacRequest)}.
     *
     * @deprecated Use {@link #verifyInboundHmac(InboundHmacRequest)} or {@link #verifyInboundHmac(HttpHeaders, String)}.
     */
    @Deprecated
    public boolean verifyHmacSignature(String signature, String timestamp, Object payload,
            String userId, String plan, List<String> roles, BigDecimal quotaRemaining) {
        if (signature == null || timestamp == null || agentSecret == null || agentSecret.isEmpty()) {
            log.warn("Missing required parameters for HMAC verification");
            return false;
        }

        try {
            String payloadString = payloadToString(payload);

            long timestampLong = Long.parseLong(timestamp);
            String userContextString = buildUserContextString(userId, plan, roles, quotaRemaining);
            String dataToSign = payloadString + timestampLong + userContextString;

            String expectedSignature = HmacUtils.calculateHmac(dataToSign, agentSecret);
            boolean isValid = HmacUtils.constantTimeEquals(expectedSignature, signature);
            if (!isValid) {
                log.warn("HMAC signature verification failed");
            }
            return isValid;
        } catch (Exception e) {
            log.error("Error verifying HMAC signature: {}", e.getMessage(), e);
            return false;
        }
    }

    private String payloadToString(Object payload) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (payload instanceof String) {
            return (String) payload;
        }
        if (payload == null) {
            return "";
        }
        return objectMapper.writeValueAsString(payload);
    }

    private static String buildUserContextString(String userId, String plan, List<String> roles, BigDecimal quotaRemaining) {
        StringBuilder sb = new StringBuilder();
        sb.append(userId != null ? userId : "");
        sb.append(plan != null ? plan : "");
        if (roles != null && !roles.isEmpty()) {
            sb.append(String.join(",", roles));
        }
        if (quotaRemaining != null) {
            sb.append(quotaRemaining.toString());
        }
        return sb.toString();
    }

    /**
     * Extracts user context from individual header values.
     *
     * @deprecated Prefer {@link #userContextFromHeaders(HttpHeaders)} or {@link #userContextFromHeaders(Map)}.
     */
    @Deprecated
    public UserContext extractUserContext(String userIdHeader, String planHeader, String rolesHeader,
            String quotaHeader, String subscriptionActiveHeader) {
        List<String> roles = parseRolesList(rolesHeader);

        BigDecimal quotaRemaining = parseQuota(quotaHeader);

        boolean subscriptionActive = false;
        if (subscriptionActiveHeader != null) {
            subscriptionActive =
                    Boolean.parseBoolean(subscriptionActiveHeader)
                            || "1".equals(subscriptionActiveHeader.trim());
        }

        return UserContext.builder()
                .userId(userIdHeader)
                .plan(planHeader)
                .roles(roles)
                .quotaRemaining(quotaRemaining)
                .subscriptionActive(subscriptionActive)
                .build();
    }

    private static List<String> parseRolesList(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of(rolesHeader.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static BigDecimal parseQuota(String quotaHeader) {
        if (quotaHeader == null || quotaHeader.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(quotaHeader.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class UserContext {
        private String userId;
        private String plan;
        private List<String> roles;
        private BigDecimal quotaRemaining;
        private boolean subscriptionActive;
    }
}
