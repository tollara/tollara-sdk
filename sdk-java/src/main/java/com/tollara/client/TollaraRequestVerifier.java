package com.tollara.client;

import com.tollara.client.model.InboundHmacRequest;
import com.tollara.client.model.SignedUserContext;
import com.tollara.common.util.GatewayHmacUserContext;
import com.tollara.common.util.HmacUtils;
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
 * Verifies Tollara requests by validating HMAC signatures.
 * Extracts user context from X-Tollara-* headers.
 */
@Slf4j
public class TollaraRequestVerifier {

    private final String serviceSecret;
    private final ObjectMapper objectMapper;

    public TollaraRequestVerifier(String serviceSecret) {
        this.serviceSecret = serviceSecret;
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
                s.getUnitLabel(),
                request.getSigningVersion());
    }

    public boolean verifyInboundHmac(Function<String, String> getHeader, String payload) {
        if (getHeader == null) {
            return false;
        }
        SignedUserContext signed =
                parseSignedUserContext(
                        headerFrom(getHeader, TollaraHeaders.USER_ID),
                        headerFrom(getHeader, TollaraHeaders.PLAN),
                        headerFrom(getHeader, TollaraHeaders.ROLES),
                        headerFrom(getHeader, TollaraHeaders.QUOTA_REMAINING),
                        headerFrom(getHeader, TollaraHeaders.SUBSCRIPTION_ACTIVE),
                        headerFrom(getHeader, TollaraHeaders.BILLING_MODEL),
                        headerFrom(getHeader, TollaraHeaders.MEASUREMENT_TYPE),
                        headerFrom(getHeader, TollaraHeaders.UNIT_LABEL));
        return verifyInboundHmac(InboundHmacRequest.builder()
                .signature(headerFrom(getHeader, TollaraHeaders.SIGNATURE))
                .timestamp(headerFrom(getHeader, TollaraHeaders.TIMESTAMP))
                .payload(payload)
                .signedUserContext(signed)
                .signingVersion(headerFrom(getHeader, TollaraHeaders.SIGNING_VERSION))
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
                headerFrom(getHeader, TollaraHeaders.USER_ID),
                headerFrom(getHeader, TollaraHeaders.PLAN),
                headerFrom(getHeader, TollaraHeaders.ROLES),
                headerFrom(getHeader, TollaraHeaders.QUOTA_REMAINING),
                headerFrom(getHeader, TollaraHeaders.SUBSCRIPTION_ACTIVE),
                headerFrom(getHeader, TollaraHeaders.BILLING_MODEL),
                headerFrom(getHeader, TollaraHeaders.MEASUREMENT_TYPE),
                headerFrom(getHeader, TollaraHeaders.UNIT_LABEL));
    }

    public UserContext userContextFromHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return emptyUserContext();
        }
        return extractUserContext(
                getHeaderIgnoreCase(headers, TollaraHeaders.USER_ID),
                getHeaderIgnoreCase(headers, TollaraHeaders.PLAN),
                getHeaderIgnoreCase(headers, TollaraHeaders.ROLES),
                getHeaderIgnoreCase(headers, TollaraHeaders.QUOTA_REMAINING),
                getHeaderIgnoreCase(headers, TollaraHeaders.SUBSCRIPTION_ACTIVE),
                getHeaderIgnoreCase(headers, TollaraHeaders.BILLING_MODEL),
                getHeaderIgnoreCase(headers, TollaraHeaders.MEASUREMENT_TYPE),
                getHeaderIgnoreCase(headers, TollaraHeaders.UNIT_LABEL));
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
        return verifyHmacSignature(signature, timestamp, payload, userId, plan, roles, quotaRemaining,
                subscriptionActive, billingModelType, measurementType, unitLabel, null);
    }

    /**
     * Verifies gateway inbound HMAC using either v1 (quota in suffix) or v2 user-context when {@code signingVersion} is {@code "2"}.
     *
     * @param signature        observed {@link TollaraHeaders#SIGNATURE}
     * @param timestamp        observed {@link TollaraHeaders#TIMESTAMP} (decimal seconds)
     * @param payload          raw body or object serialized like the gateway
     * @param userId           user id from headers
     * @param plan             plan from headers
     * @param roles            roles from headers
     * @param quotaRemaining   quota from headers (v1 only; ignored for v2)
     * @param subscriptionActive subscription flag from headers
     * @param billingModelType billing model from headers
     * @param measurementType  measurement type from headers
     * @param unitLabel        unit label from headers
     * @param signingVersion   {@link TollaraHeaders#SIGNING_VERSION} value; {@code "2"} selects HMAC user-context v2 (no quota segment)
     * @return {@code true} if the signature matches
     */
    public boolean verifyHmacSignature(String signature, String timestamp, Object payload,
            String userId, String plan, List<String> roles, BigDecimal quotaRemaining,
            boolean subscriptionActive, String billingModelType, String measurementType, String unitLabel,
            String signingVersion) {
        if (signature == null || timestamp == null || serviceSecret == null || serviceSecret.isEmpty()) {
            log.warn("Missing required parameters for HMAC verification");
            return false;
        }

        try {
            String payloadString = payloadToString(payload);

            long timestampLong = Long.parseLong(timestamp);
            String userContextString;
            if ("2".equals(signingVersion)) {
                userContextString = GatewayHmacUserContext.buildV2(
                        userId, plan, roles, subscriptionActive,
                        billingModelType, measurementType, unitLabel);
            } else {
                userContextString = GatewayHmacUserContext.build(
                        userId, plan, roles, quotaRemaining, subscriptionActive,
                        billingModelType, measurementType, unitLabel);
            }
            String dataToSign = payloadString + timestampLong + userContextString;

            String expectedSignature = HmacUtils.calculateHmac(dataToSign, serviceSecret);
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
