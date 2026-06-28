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
import java.util.Set;
import java.util.function.Function;

/**
 * Verifies Tollara requests by validating HMAC signatures.
 * Extracts user context from X-Tollara-* headers.
 */
@Slf4j
public class TollaraRequestVerifier {

    private static final Set<String> INVOKE_ELIGIBLE_STATUSES = Set.of(
            "ACTIVE", "TRIAL", "CANCELLING", "CANCELLING_PENDING");

    private final String serviceSecret;
    private final ObjectMapper objectMapper;

    public TollaraRequestVerifier(String serviceSecret) {
        this.serviceSecret = serviceSecret;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Returns {@code true} when {@code subscriptionStatus} is invoke-eligible
     * ({@code ACTIVE}, {@code TRIAL}, {@code CANCELLING}, {@code CANCELLING_PENDING}).
     */
    public static boolean grantsAccess(String subscriptionStatus) {
        if (subscriptionStatus == null || subscriptionStatus.isBlank()) {
            return false;
        }
        return INVOKE_ELIGIBLE_STATUSES.contains(subscriptionStatus.trim());
    }

    public boolean verifyInboundHmac(InboundHmacRequest request) {
        if (request == null || request.getSignedUserContext() == null) {
            log.warn("Missing inbound HMAC request or signed user context");
            return false;
        }
        SignedUserContext s = request.getSignedUserContext();
        String signingVersion = request.getSigningVersion();
        if (GatewayHmacUserContext.SIGNING_VERSION_V3.equals(signingVersion)) {
            return verifyHmacSignatureV3(
                    request.getSignature(),
                    request.getTimestamp(),
                    request.getPayload(),
                    s.getUserId(),
                    s.getServiceProductId(),
                    s.getRoles(),
                    s.getSubscriptionStatus(),
                    s.getBillingModelType(),
                    s.getMeasurementType(),
                    s.getUnitLabel());
        }
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
                signingVersion);
    }

    public boolean verifyInboundHmac(Function<String, String> getHeader, String payload) {
        if (getHeader == null) {
            return false;
        }
        String signingVersion = headerFrom(getHeader, TollaraHeaders.SIGNING_VERSION);
        SignedUserContext signed = parseSignedUserContext(
                headerFrom(getHeader, TollaraHeaders.USER_ID),
                headerFrom(getHeader, TollaraHeaders.SERVICE_PRODUCT_ID),
                headerFrom(getHeader, TollaraHeaders.ROLES),
                headerFrom(getHeader, TollaraHeaders.SUBSCRIPTION_STATUS),
                headerFrom(getHeader, TollaraHeaders.BILLING_MODEL),
                headerFrom(getHeader, TollaraHeaders.MEASUREMENT_TYPE),
                headerFrom(getHeader, TollaraHeaders.UNIT_LABEL),
                headerFrom(getHeader, TollaraHeaders.PLAN),
                headerFrom(getHeader, TollaraHeaders.QUOTA_REMAINING),
                headerFrom(getHeader, TollaraHeaders.SUBSCRIPTION_ACTIVE),
                signingVersion);
        return verifyInboundHmac(InboundHmacRequest.builder()
                .signature(headerFrom(getHeader, TollaraHeaders.SIGNATURE))
                .timestamp(headerFrom(getHeader, TollaraHeaders.TIMESTAMP))
                .payload(payload)
                .signedUserContext(signed)
                .signingVersion(signingVersion)
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
            String serviceProductId,
            String rolesHeader,
            String subscriptionStatusHeader,
            String billingModelHeader,
            String measurementTypeHeader,
            String unitLabelHeader,
            String planHeader,
            String quotaHeader,
            String subscriptionActiveHeader,
            String signingVersion) {
        List<String> roles = parseRolesList(rolesHeader);
        BigDecimal quota = parseQuota(quotaHeader);
        return SignedUserContext.builder()
                .userId(userId)
                .serviceProductId(emptyToNull(serviceProductId))
                .roles(roles)
                .subscriptionStatus(emptyToNull(subscriptionStatusHeader))
                .billingModelType(emptyToNull(billingModelHeader))
                .measurementType(emptyToNull(measurementTypeHeader))
                .unitLabel(emptyToNull(unitLabelHeader))
                .plan(planHeader)
                .quotaRemaining(quota)
                .subscriptionActive(parseSubscriptionActiveFlag(subscriptionActiveHeader))
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
                headerFrom(getHeader, TollaraHeaders.SERVICE_PRODUCT_ID),
                headerFrom(getHeader, TollaraHeaders.ROLES),
                headerFrom(getHeader, TollaraHeaders.SUBSCRIPTION_STATUS),
                headerFrom(getHeader, TollaraHeaders.BILLING_MODEL),
                headerFrom(getHeader, TollaraHeaders.MEASUREMENT_TYPE),
                headerFrom(getHeader, TollaraHeaders.UNIT_LABEL),
                headerFrom(getHeader, TollaraHeaders.PLAN),
                headerFrom(getHeader, TollaraHeaders.QUOTA_REMAINING),
                headerFrom(getHeader, TollaraHeaders.SUBSCRIPTION_ACTIVE));
    }

    public UserContext userContextFromHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return emptyUserContext();
        }
        return extractUserContext(
                getHeaderIgnoreCase(headers, TollaraHeaders.USER_ID),
                getHeaderIgnoreCase(headers, TollaraHeaders.SERVICE_PRODUCT_ID),
                getHeaderIgnoreCase(headers, TollaraHeaders.ROLES),
                getHeaderIgnoreCase(headers, TollaraHeaders.SUBSCRIPTION_STATUS),
                getHeaderIgnoreCase(headers, TollaraHeaders.BILLING_MODEL),
                getHeaderIgnoreCase(headers, TollaraHeaders.MEASUREMENT_TYPE),
                getHeaderIgnoreCase(headers, TollaraHeaders.UNIT_LABEL),
                getHeaderIgnoreCase(headers, TollaraHeaders.PLAN),
                getHeaderIgnoreCase(headers, TollaraHeaders.QUOTA_REMAINING),
                getHeaderIgnoreCase(headers, TollaraHeaders.SUBSCRIPTION_ACTIVE));
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
     * Verifies gateway inbound HMAC using v1 (quota in suffix) or v2 user-context when {@code signingVersion} is {@code "2"}.
     */
    public boolean verifyHmacSignature(String signature, String timestamp, Object payload,
            String userId, String plan, List<String> roles, BigDecimal quotaRemaining,
            boolean subscriptionActive, String billingModelType, String measurementType, String unitLabel,
            String signingVersion) {
        if (GatewayHmacUserContext.SIGNING_VERSION_V3.equals(signingVersion)) {
            log.warn("verifyHmacSignature called with signingVersion 3; use verifyHmacSignatureV3");
            return false;
        }
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
            return verifySignature(signature, payloadString, timestampLong, userContextString);
        } catch (Exception e) {
            log.error("Error verifying HMAC signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Verifies gateway inbound HMAC using v3 user-context ({@code serviceProductId}, {@code subscriptionStatus}).
     */
    public boolean verifyHmacSignatureV3(String signature, String timestamp, Object payload,
            String userId, String serviceProductId, List<String> roles, String subscriptionStatus,
            String billingModelType, String measurementType, String unitLabel) {
        if (signature == null || timestamp == null || serviceSecret == null || serviceSecret.isEmpty()) {
            log.warn("Missing required parameters for HMAC verification");
            return false;
        }

        try {
            String payloadString = payloadToString(payload);
            long timestampLong = Long.parseLong(timestamp);
            String userContextString = GatewayHmacUserContext.buildV3(
                    userId, serviceProductId, roles, subscriptionStatus,
                    billingModelType, measurementType, unitLabel);
            return verifySignature(signature, payloadString, timestampLong, userContextString);
        } catch (Exception e) {
            log.error("Error verifying HMAC signature: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean verifySignature(String signature, String payloadString, long timestampLong, String userContextString)
            throws Exception {
        String dataToSign = payloadString + timestampLong + userContextString;
        String expectedSignature = HmacUtils.calculateHmac(dataToSign, serviceSecret);
        boolean isValid = HmacUtils.constantTimeEquals(expectedSignature, signature);
        if (!isValid) {
            log.warn("HMAC signature verification failed");
        }
        return isValid;
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

    public UserContext extractUserContext(String userIdHeader, String serviceProductIdHeader, String rolesHeader,
            String subscriptionStatusHeader, String billingModelHeader, String measurementTypeHeader,
            String unitLabelHeader, String planHeader, String quotaHeader, String subscriptionActiveHeader) {
        List<String> roles = parseRolesList(rolesHeader);

        return UserContext.builder()
                .userId(userIdHeader)
                .serviceProductId(emptyToNull(serviceProductIdHeader))
                .roles(roles)
                .subscriptionStatus(emptyToNull(subscriptionStatusHeader))
                .billingModelType(emptyToNull(billingModelHeader))
                .measurementType(emptyToNull(measurementTypeHeader))
                .unitLabel(emptyToNull(unitLabelHeader))
                .plan(planHeader)
                .quotaRemaining(parseQuota(quotaHeader))
                .subscriptionActive(parseSubscriptionActiveFlag(subscriptionActiveHeader))
                .build();
    }

    /** @deprecated use {@link #extractUserContext(String, String, String, String, String, String, String, String, String, String)} */
    @Deprecated
    public UserContext extractUserContext(String userIdHeader, String serviceProductIdHeader, String rolesHeader,
            String subscriptionStatusHeader, String billingModelHeader, String measurementTypeHeader,
            String unitLabelHeader) {
        return extractUserContext(userIdHeader, serviceProductIdHeader, rolesHeader, subscriptionStatusHeader,
                billingModelHeader, measurementTypeHeader, unitLabelHeader, null, null, null);
    }

    /** @deprecated use {@link #extractUserContext(String, String, String, String, String, String, String)} */
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
        private String serviceProductId;
        private List<String> roles;
        private String subscriptionStatus;
        private String billingModelType;
        private String measurementType;
        private String unitLabel;

        /** @deprecated v1/v2 only; use {@link #serviceProductId} for signing version 3. */
        @Deprecated
        private String plan;

        /** @deprecated v1 only. */
        @Deprecated
        private BigDecimal quotaRemaining;

        /** @deprecated v1/v2 only; use {@link #subscriptionStatus} and {@link TollaraRequestVerifier#grantsAccess(String)}. */
        @Deprecated
        private boolean subscriptionActive;
    }
}
