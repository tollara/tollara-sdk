package com.agentvend.client;

import com.agentvend.client.model.InboundHmacRequest;
import com.agentvend.client.model.SignedUserContext;
import com.agentvend.common.util.GatewayHmacUserContext;
import com.agentvend.common.util.HmacUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Verifies AgentVend requests by validating HMAC signatures.
 * Extracts user context from X-AgentVend-* headers.
 */
@Slf4j
public class AgentVendRequestVerifier {

    private final String agentSecret;
    private final ObjectMapper objectMapper;

    public AgentVendRequestVerifier(String agentSecret) {
        this.agentSecret = agentSecret;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

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

    public boolean verifyInboundHmac(Function<String, String> getHeader, String payload) {
        if (getHeader == null) {
            return false;
        }
        SignedUserContext signed =
                parseSignedUserContext(
                        headerFrom(getHeader, AgentVendHeaders.USER_ID),
                        headerFrom(getHeader, AgentVendHeaders.PLAN),
                        headerFrom(getHeader, AgentVendHeaders.ROLES),
                        headerFrom(getHeader, AgentVendHeaders.QUOTA_REMAINING),
                        headerFrom(getHeader, AgentVendHeaders.SUBSCRIPTION_ACTIVE),
                        headerFrom(getHeader, AgentVendHeaders.BILLING_MODEL),
                        headerFrom(getHeader, AgentVendHeaders.MEASUREMENT_TYPE),
                        headerFrom(getHeader, AgentVendHeaders.UNIT_LABEL));
        return verifyInboundHmac(InboundHmacRequest.builder()
                .signature(headerFrom(getHeader, AgentVendHeaders.SIGNATURE))
                .timestamp(headerFrom(getHeader, AgentVendHeaders.TIMESTAMP))
                .payload(payload)
                .signedUserContext(signed)
                .build());
    }

    public boolean verifyInboundHmac(Map<String, String> headers, String payload) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        return verifyInboundHmac(name -> getHeaderIgnoreCase(headers, name), payload);
    }

    private static String headerFrom(Function<String, String> getHeader, String canonicalName) {
        if (canonicalName == null) {
            return null;
        }
        String v = getHeader.apply(canonicalName);
        if (v != null) {
            return v;
        }
        return getHeader.apply(canonicalName.toLowerCase(Locale.ROOT));
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

    public UserContext userContextFromHeaders(Function<String, String> getHeader) {
        if (getHeader == null) {
            return emptyUserContext();
        }
        return extractUserContext(
                headerFrom(getHeader, AgentVendHeaders.USER_ID),
                headerFrom(getHeader, AgentVendHeaders.PLAN),
                headerFrom(getHeader, AgentVendHeaders.ROLES),
                headerFrom(getHeader, AgentVendHeaders.QUOTA_REMAINING),
                headerFrom(getHeader, AgentVendHeaders.SUBSCRIPTION_ACTIVE),
                headerFrom(getHeader, AgentVendHeaders.BILLING_MODEL),
                headerFrom(getHeader, AgentVendHeaders.MEASUREMENT_TYPE),
                headerFrom(getHeader, AgentVendHeaders.UNIT_LABEL));
    }

    public UserContext userContextFromHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return emptyUserContext();
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

    /**
     * Verifies the inbound HMAC; if valid, returns user context from the same headers.
     * If verification fails, returns empty — do not treat headers as authenticated.
     */
    public Optional<UserContext> verifyInboundHmacAndGetUserContext(Function<String, String> getHeader, String payload) {
        if (!verifyInboundHmac(getHeader, payload)) {
            return Optional.empty();
        }
        return Optional.of(userContextFromHeaders(getHeader));
    }

    /**
     * Same as {@link #verifyInboundHmacAndGetUserContext(Function, String)} for a header map (case-insensitive keys).
     */
    public Optional<UserContext> verifyInboundHmacAndGetUserContext(Map<String, String> headers, String payload) {
        if (!verifyInboundHmac(headers, payload)) {
            return Optional.empty();
        }
        return Optional.of(userContextFromHeaders(headers));
    }

    private static UserContext emptyUserContext() {
        return UserContext.builder()
                .roles(Collections.emptyList())
                .subscriptionActive(false)
                .build();
    }

    private static String getHeaderIgnoreCase(Map<String, String> headers, String canonicalName) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(canonicalName)) {
                return e.getValue();
            }
        }
        return null;
    }

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
