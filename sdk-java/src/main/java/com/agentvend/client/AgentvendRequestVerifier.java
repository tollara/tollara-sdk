package com.agentvend.client;

import com.agentvend.client.model.InboundHmacRequest;
import com.agentvend.client.model.SignedUserContext;
import com.agentvend.common.util.GatewayHmacUserContext;
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
                s.getQuotaRemaining(),
                s.isSubscriptionActive(),
                s.getBillingModelType(),
                s.getMeasurementType(),
                s.getUnitLabel());
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
                parseSignedUserContext(
                        headers.getFirst(AgentVendHeaders.USER_ID),
                        headers.getFirst(AgentVendHeaders.PLAN),
                        headers.getFirst(AgentVendHeaders.ROLES),
                        headers.getFirst(AgentVendHeaders.QUOTA_REMAINING),
                        headers.getFirst(AgentVendHeaders.SUBSCRIPTION_ACTIVE),
                        headers.getFirst(AgentVendHeaders.BILLING_MODEL),
                        headers.getFirst(AgentVendHeaders.MEASUREMENT_TYPE),
                        headers.getFirst(AgentVendHeaders.UNIT_LABEL));
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
                        getHeaderIgnoreCase(headers, AgentVendHeaders.QUOTA_REMAINING),
                        getHeaderIgnoreCase(headers, AgentVendHeaders.SUBSCRIPTION_ACTIVE),
                        getHeaderIgnoreCase(headers, AgentVendHeaders.BILLING_MODEL),
                        getHeaderIgnoreCase(headers, AgentVendHeaders.MEASUREMENT_TYPE),
                        getHeaderIgnoreCase(headers, AgentVendHeaders.UNIT_LABEL));
        return verifyInboundHmac(InboundHmacRequest.builder()
                .signature(signature)
                .timestamp(timestamp)
                .payload(payload)
                .signedUserContext(signed)
                .build());
    }

    private static SignedUserContext parseSignedUserContext(
            String userId,
            String plan,
            String rolesHeader,
            String quotaHeader,
            String subscriptionActiveHeader,
            String billingModelHeader,
            String measurementTypeHeader,
            String unitLabelHeader) {
        List<String> roles = parseRolesList(rolesHeader);
        BigDecimal quota = parseQuota(quotaHeader);
        return SignedUserContext.builder()
                .userId(userId)
                .plan(plan)
                .roles(roles)
                .quotaRemaining(quota)
                .subscriptionActive(parseSubscriptionActiveFlag(subscriptionActiveHeader))
                .billingModelType(emptyToNull(billingModelHeader))
                .measurementType(emptyToNull(measurementTypeHeader))
                .unitLabel(emptyToNull(unitLabelHeader))
                .build();
    }

    private static String emptyToNull(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return s;
    }

    private static boolean parseSubscriptionActiveFlag(String header) {
        if (header == null || header.isEmpty()) {
            return false;
        }
        return Boolean.parseBoolean(header.trim()) || "1".equals(header.trim());
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
                headers.getFirst(AgentVendHeaders.SUBSCRIPTION_ACTIVE),
                headers.getFirst(AgentVendHeaders.BILLING_MODEL),
                headers.getFirst(AgentVendHeaders.MEASUREMENT_TYPE),
                headers.getFirst(AgentVendHeaders.UNIT_LABEL));
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
                getHeaderIgnoreCase(headers, AgentVendHeaders.SUBSCRIPTION_ACTIVE),
                getHeaderIgnoreCase(headers, AgentVendHeaders.BILLING_MODEL),
                getHeaderIgnoreCase(headers, AgentVendHeaders.MEASUREMENT_TYPE),
                getHeaderIgnoreCase(headers, AgentVendHeaders.UNIT_LABEL));
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
     * Verifies the HMAC signature with the extended gateway user-context string (see {@link GatewayHmacUserContext}).
     * Prefer {@link #verifyInboundHmac(InboundHmacRequest)}.
     *
     * @deprecated Use {@link #verifyInboundHmac(InboundHmacRequest)} or {@link #verifyInboundHmac(HttpHeaders, String)}.
     */
    @Deprecated
    public boolean verifyHmacSignature(String signature, String timestamp, Object payload,
            String userId, String plan, List<String> roles, BigDecimal quotaRemaining,
            boolean subscriptionActive, String billingModelType, String measurementType, String unitLabel) {
        if (signature == null || timestamp == null || agentSecret == null || agentSecret.isEmpty()) {
            log.warn("Missing required parameters for HMAC verification");
            return false;
        }

        try {
            String payloadString = payloadToString(payload);

            long timestampLong = Long.parseLong(timestamp);
            String userContextString = GatewayHmacUserContext.build(
                    userId, plan, roles, quotaRemaining, subscriptionActive,
                    billingModelType, measurementType, unitLabel);
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

    /**
     * Extracts user context from individual header values.
     *
     * @deprecated Prefer {@link #userContextFromHeaders(HttpHeaders)} or {@link #userContextFromHeaders(Map)}.
     */
    @Deprecated
    public UserContext extractUserContext(String userIdHeader, String planHeader, String rolesHeader,
            String quotaHeader, String subscriptionActiveHeader,
            String billingModelHeader, String measurementTypeHeader, String unitLabelHeader) {
        List<String> roles = parseRolesList(rolesHeader);

        BigDecimal quotaRemaining = parseQuota(quotaHeader);

        boolean subscriptionActive = parseSubscriptionActiveFlag(subscriptionActiveHeader);

        return UserContext.builder()
                .userId(userIdHeader)
                .plan(planHeader)
                .roles(roles)
                .quotaRemaining(quotaRemaining)
                .subscriptionActive(subscriptionActive)
                .billingModelType(emptyToNull(billingModelHeader))
                .measurementType(emptyToNull(measurementTypeHeader))
                .unitLabel(emptyToNull(unitLabelHeader))
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
        private String billingModelType;
        private String measurementType;
        private String unitLabel;
    }
}
