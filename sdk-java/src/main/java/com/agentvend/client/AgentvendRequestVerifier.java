package com.agentvend.client;

import com.agentvend.common.util.HmacUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

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
     * Verifies the HMAC signature from the request headers.
     * Canonical string: payload + timestamp + userContextString.
     */
    public boolean verifyHmacSignature(String signature, String timestamp, Object payload,
                                        String userId, String plan, List<String> roles, BigDecimal quotaRemaining) {
        if (signature == null || timestamp == null || agentSecret == null || agentSecret.isEmpty()) {
            log.warn("Missing required parameters for HMAC verification");
            return false;
        }

        try {
            String payloadString;
            if (payload instanceof String) {
                payloadString = (String) payload;
            } else if (payload == null) {
                payloadString = "";
            } else {
                payloadString = objectMapper.writeValueAsString(payload);
            }

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
     * Extracts user context from X-AgentVend-* headers.
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
