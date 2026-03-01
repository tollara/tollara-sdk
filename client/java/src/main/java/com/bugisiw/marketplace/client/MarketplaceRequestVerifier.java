package com.bugisiw.marketplace.client;

import com.bugisiw.marketplace.common.util.HmacUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Verifies marketplace requests by validating HMAC signatures.
 * Extracts user context from X-Marketplace-* headers.
 */
@Slf4j
public class MarketplaceRequestVerifier {

    private final String agentSecret;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new MarketplaceRequestVerifier.
     *
     * @param agentSecret The agent's secret key for HMAC verification
     */
    public MarketplaceRequestVerifier(String agentSecret) {
        this.agentSecret = agentSecret;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Verifies the HMAC signature from the request headers.
     * The signature is calculated as: HMAC(payload + timestamp + userContext, secret)
     * where userContext = userId + plan + roles + quotaRemaining
     *
     * @param signature The signature from X-Marketplace-Signature header
     * @param timestamp The timestamp from X-Marketplace-Timestamp header
     * @param payload The request payload (can be String or Object)
     * @param userId The user ID from X-Marketplace-User-ID header
     * @param plan The plan from X-Marketplace-Plan header (optional)
     * @param roles The roles from X-Marketplace-Roles header (optional)
     * @param quotaRemaining The quota from X-Marketplace-Quota-Remaining header (optional)
     * @return true if signature is valid, false otherwise
     */
    public boolean verifyHmacSignature(String signature, String timestamp, Object payload,
                                        String userId, String plan, List<String> roles, BigDecimal quotaRemaining) {
        if (signature == null || timestamp == null || agentSecret == null || agentSecret.isEmpty()) {
            log.warn("Missing required parameters for HMAC verification");
            return false;
        }

        try {
            // Serialize payload to JSON string if it's an object
            // Must match how gateway service serializes (ObjectMapper.writeValueAsString)
            String payloadString;
            if (payload instanceof String) {
                payloadString = (String) payload;
            } else if (payload == null) {
                payloadString = "";
            } else {
                // Serialize to JSON to match gateway service serialization
                payloadString = objectMapper.writeValueAsString(payload);
            }

            long timestampLong = Long.parseLong(timestamp);
            
            // Build user context string (must match gateway service format)
            String userContextString = buildUserContextString(userId, plan, roles, quotaRemaining);
            String dataToSign = payloadString + timestampLong + userContextString;
            
            // Signature is: HMAC(payload + timestamp + userContext, secret)
            String expectedSignature = HmacUtils.calculateHmac(dataToSign, agentSecret);
            boolean isValid = expectedSignature.equals(signature);
            if (!isValid) {
                log.warn("HMAC signature verification failed - expected: {}, received: {}, dataToSign: '{}'", 
                        expectedSignature, signature, dataToSign);
            }
            return isValid;
        } catch (Exception e) {
            log.error("Error verifying HMAC signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Builds a string representation of user context for HMAC signing.
     * Must match the format used in gateway service.
     *
     * @param userId The user ID
     * @param plan The subscription plan
     * @param roles The user roles
     * @param quotaRemaining The remaining quota
     * @return String representation of user context
     */
    private String buildUserContextString(String userId, String plan, List<String> roles, BigDecimal quotaRemaining) {
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
     * Extracts user context from X-Marketplace-* headers.
     *
     * @param userIdHeader The X-Marketplace-User-ID header value
     * @param planHeader The X-Marketplace-Plan header value (optional)
     * @param rolesHeader The X-Marketplace-Roles header value (optional, comma-separated)
     * @param quotaHeader The X-Marketplace-Quota-Remaining header value (optional)
     * @param subscriptionActiveHeader The X-Marketplace-Subscription-Active header value (optional)
     * @return UserContext containing extracted information
     */
    public UserContext extractUserContext(String userIdHeader, String planHeader, String rolesHeader,
                                          String quotaHeader, String subscriptionActiveHeader) {
        List<String> roles = Collections.emptyList();
        if (rolesHeader != null && !rolesHeader.isEmpty()) {
            roles = List.of(rolesHeader.split(","));
        }

        BigDecimal quotaRemaining = null;
        if (quotaHeader != null && !quotaHeader.isEmpty()) {
            try {
                quotaRemaining = new BigDecimal(quotaHeader);
            } catch (NumberFormatException e) {
                log.warn("Invalid quota value in header: {}", quotaHeader);
            }
        }

        boolean subscriptionActive = false;
        if (subscriptionActiveHeader != null) {
            subscriptionActive = Boolean.parseBoolean(subscriptionActiveHeader);
        }

        return UserContext.builder()
                .userId(userIdHeader)
                .plan(planHeader)
                .roles(roles)
                .quotaRemaining(quotaRemaining)
                .subscriptionActive(subscriptionActive)
                .build();
    }

    /**
     * User context extracted from X-Marketplace-* headers.
     */
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

