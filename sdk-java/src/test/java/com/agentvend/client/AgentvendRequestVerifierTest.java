package com.agentvend.client;

import com.agentvend.client.model.InboundHmacRequest;
import com.agentvend.client.model.SignedUserContext;
import com.agentvend.common.util.GatewayHmacUserContext;
import com.agentvend.common.util.HmacUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentvendRequestVerifierTest {

    private static final String SECRET = "my-agent-secret";
    private static final String SECRET_OWNER = "test-agent-secret";

    /** Extended canonical string: legacy user block + subscriptionActive + billing fields. */
    @Test
    void verifyInboundHmac_acceptsHmacSpecVectorExtended() {
        String payload = "";
        String timestamp = "1700000000";
        SignedUserContext signed = SignedUserContext.builder()
                .userId("user1")
                .plan("plan1")
                .roles(List.of("role1", "role2"))
                .quotaRemaining(new BigDecimal("10"))
                .subscriptionActive(false)
                .build();
        String userContextString = GatewayHmacUserContext.build(
                signed.getUserId(),
                signed.getPlan(),
                signed.getRoles(),
                signed.getQuotaRemaining(),
                signed.isSubscriptionActive(),
                signed.getBillingModelType(),
                signed.getMeasurementType(),
                signed.getUnitLabel());
        String dataToSign = payload + Long.parseLong(timestamp) + userContextString;
        String signature = HmacUtils.calculateHmac(dataToSign, SECRET);

        AgentvendRequestVerifier verifier = new AgentvendRequestVerifier(SECRET);
        InboundHmacRequest req = InboundHmacRequest.builder()
                .signature(signature)
                .timestamp(timestamp)
                .payload(payload)
                .signedUserContext(signed)
                .build();
        assertThat(verifier.verifyInboundHmac(req)).isTrue();
    }

    @Test
    void verifyInboundHmac_withHttpHeaders_succeeds() {
        String payload = "";
        String timestamp = "1700000000";
        SignedUserContext signed = SignedUserContext.builder()
                .userId("user1")
                .plan("plan1")
                .roles(List.of("role1", "role2"))
                .quotaRemaining(new BigDecimal("10"))
                .subscriptionActive(false)
                .build();
        String userContextString = GatewayHmacUserContext.build(
                signed.getUserId(),
                signed.getPlan(),
                signed.getRoles(),
                signed.getQuotaRemaining(),
                signed.isSubscriptionActive(),
                null,
                null,
                null);
        String dataToSign = payload + Long.parseLong(timestamp) + userContextString;
        String signature = HmacUtils.calculateHmac(dataToSign, SECRET);

        HttpHeaders headers = new HttpHeaders();
        headers.add(AgentVendHeaders.SIGNATURE, signature);
        headers.add(AgentVendHeaders.TIMESTAMP, timestamp);
        headers.add(AgentVendHeaders.USER_ID, "user1");
        headers.add(AgentVendHeaders.PLAN, "plan1");
        headers.add(AgentVendHeaders.ROLES, "role1,role2");
        headers.add(AgentVendHeaders.QUOTA_REMAINING, "10");
        headers.add(AgentVendHeaders.SUBSCRIPTION_ACTIVE, "false");

        AgentvendRequestVerifier verifier = new AgentvendRequestVerifier(SECRET);
        assertThat(verifier.verifyInboundHmac(headers, payload)).isTrue();
    }

    @Test
    void verifyInboundHmac_withLowercaseHeaderMap_succeeds() {
        String payload = "";
        String timestamp = "1700000000";
        SignedUserContext signed = SignedUserContext.builder()
                .userId("user1")
                .plan("plan1")
                .roles(List.of("role1", "role2"))
                .quotaRemaining(new BigDecimal("10"))
                .subscriptionActive(false)
                .build();
        String userContextString = GatewayHmacUserContext.build(
                signed.getUserId(),
                signed.getPlan(),
                signed.getRoles(),
                signed.getQuotaRemaining(),
                signed.isSubscriptionActive(),
                null,
                null,
                null);
        String dataToSign = payload + Long.parseLong(timestamp) + userContextString;
        String signature = HmacUtils.calculateHmac(dataToSign, SECRET);

        Map<String, String> headers = new HashMap<>();
        headers.put("x-agentvend-signature", signature);
        headers.put("x-agentvend-timestamp", timestamp);
        headers.put("x-agentvend-user-id", "user1");
        headers.put("x-agentvend-plan", "plan1");
        headers.put("x-agentvend-roles", "role1,role2");
        headers.put("x-agentvend-quota-remaining", "10");
        headers.put("x-agentvend-subscription-active", "false");

        AgentvendRequestVerifier verifier = new AgentvendRequestVerifier(SECRET);
        assertThat(verifier.verifyInboundHmac(headers, payload)).isTrue();
    }

    @Test
    void verify_matches_gateway_owner_like_context() {
        String payload = "{\"hello\":1}";
        long ts = 1700000000L;
        String userId = "user-1";
        String plan = "owner";
        List<String> roles = List.of();
        BigDecimal quota = BigDecimal.valueOf(Long.MAX_VALUE);
        boolean subscriptionActive = true;
        String userCtx = GatewayHmacUserContext.build(
                userId, plan, roles, quota, subscriptionActive, null, null, null);
        String dataToSign = payload + ts + userCtx;
        String signature = HmacUtils.calculateHmac(dataToSign, SECRET_OWNER);

        AgentvendRequestVerifier verifier = new AgentvendRequestVerifier(SECRET_OWNER);
        assertThat(verifier.verifyHmacSignature(
                signature,
                String.valueOf(ts),
                payload,
                userId,
                plan,
                roles,
                quota,
                subscriptionActive,
                null,
                null,
                null))
                .isTrue();
    }

    @Test
    void verify_matches_gateway_subscriber_with_billing_headers() {
        String payload = "";
        long ts = 1710000000L;
        String userId = "sub-user";
        String plan = "basic";
        List<String> roles = List.of("roleA", "roleB");
        BigDecimal quota = new BigDecimal("50");
        boolean subscriptionActive = true;
        String billing = "SUBSCRIPTION";
        String measurement = "PER_REQUEST";
        String unit = "request";
        String userCtx = GatewayHmacUserContext.build(
                userId, plan, roles, quota, subscriptionActive, billing, measurement, unit);
        String dataToSign = payload + ts + userCtx;
        String signature = HmacUtils.calculateHmac(dataToSign, SECRET_OWNER);

        AgentvendRequestVerifier verifier = new AgentvendRequestVerifier(SECRET_OWNER);
        assertThat(verifier.verifyHmacSignature(
                signature,
                String.valueOf(ts),
                payload,
                userId,
                plan,
                roles,
                quota,
                subscriptionActive,
                billing,
                measurement,
                unit))
                .isTrue();
    }

    @Test
    void userContextFromHeaders_parsesSubscriptionAndBilling() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(AgentVendHeaders.USER_ID, "u1");
        headers.add(AgentVendHeaders.SUBSCRIPTION_ACTIVE, "true");
        headers.add(AgentVendHeaders.BILLING_MODEL, "USAGE_POSTPAID");
        headers.add(AgentVendHeaders.MEASUREMENT_TYPE, "PER_TOKEN");
        headers.add(AgentVendHeaders.UNIT_LABEL, "token");

        AgentvendRequestVerifier verifier = new AgentvendRequestVerifier(SECRET);
        AgentvendRequestVerifier.UserContext ctx = verifier.userContextFromHeaders(headers);
        assertThat(ctx.getUserId()).isEqualTo("u1");
        assertThat(ctx.isSubscriptionActive()).isTrue();
        assertThat(ctx.getBillingModelType()).isEqualTo("USAGE_POSTPAID");
        assertThat(ctx.getMeasurementType()).isEqualTo("PER_TOKEN");
        assertThat(ctx.getUnitLabel()).isEqualTo("token");
    }
}
