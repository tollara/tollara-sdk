package com.tollara.client;

import com.tollara.client.model.InboundHmacRequest;
import com.tollara.client.model.SignedUserContext;
import com.tollara.common.util.GatewayHmacUserContext;
import com.tollara.common.util.HmacUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TollaraRequestVerifierTest {

    private static final String SECRET = "my-agent-secret";
    private static final String SECRET_OWNER = "test-agent-secret";

    @Test
    void verifyInboundHmac_acceptsGatewayHmacV3_whenSigningVersionHeaderIs3() throws Exception {
        String payload = "";
        String timestamp = "1700000000";
        SignedUserContext signed = SignedUserContext.builder()
                .userId("user1")
                .serviceProductId("prod-1")
                .roles(List.of("role1", "role2"))
                .subscriptionStatus("ACTIVE")
                .billingModelType("SUBSCRIPTION")
                .measurementType("PER_REQUEST")
                .unitLabel("request")
                .build();
        String userContextString = GatewayHmacUserContext.buildV3(
                signed.getUserId(),
                signed.getServiceProductId(),
                signed.getRoles(),
                signed.getSubscriptionStatus(),
                signed.getBillingModelType(),
                signed.getMeasurementType(),
                signed.getUnitLabel());
        String dataToSign = payload + Long.parseLong(timestamp) + userContextString;
        String signature = HmacUtils.calculateHmac(dataToSign, SECRET);

        Map<String, String> headers = new HashMap<>();
        headers.put(TollaraHeaders.SIGNATURE, signature);
        headers.put(TollaraHeaders.TIMESTAMP, timestamp);
        headers.put(TollaraHeaders.SIGNING_VERSION, "3");
        headers.put(TollaraHeaders.USER_ID, "user1");
        headers.put(TollaraHeaders.SERVICE_PRODUCT_ID, "prod-1");
        headers.put(TollaraHeaders.ROLES, "role1,role2");
        headers.put(TollaraHeaders.SUBSCRIPTION_STATUS, "ACTIVE");
        headers.put(TollaraHeaders.BILLING_MODEL, "SUBSCRIPTION");
        headers.put(TollaraHeaders.MEASUREMENT_TYPE, "PER_REQUEST");
        headers.put(TollaraHeaders.UNIT_LABEL, "request");

        TollaraRequestVerifier verifier = new TollaraRequestVerifier(SECRET);
        assertThat(verifier.verifyInboundHmac(headers::get, payload)).isTrue();

        Optional<TollaraRequestVerifier.UserContext> ctx =
                verifier.verifyInboundHmacAndGetUserContext(headers::get, payload);
        assertThat(ctx).isPresent();
        assertThat(ctx.get().getServiceProductId()).isEqualTo("prod-1");
        assertThat(ctx.get().getSubscriptionStatus()).isEqualTo("ACTIVE");
        assertThat(TollaraRequestVerifier.grantAccess(ctx.get().getSubscriptionStatus())).isTrue();
    }

    @Test
    void grantAccess_returnsFalseForNonEligibleStatus() {
        assertThat(TollaraRequestVerifier.grantAccess("EXPIRED")).isFalse();
        assertThat(TollaraRequestVerifier.grantAccess(null)).isFalse();
        assertThat(TollaraRequestVerifier.grantAccess("CANCELLING_PENDING")).isTrue();
    }

    @Test
    void verifyInboundHmac_acceptsGatewayHmacV2_whenSigningVersionHeaderIs2() throws Exception {
        String payload = "";
        String timestamp = "1700000000";
        SignedUserContext signed = SignedUserContext.builder()
                .userId("user1")
                .plan("plan1")
                .roles(List.of("role1", "role2"))
                .quotaRemaining(null)
                .subscriptionActive(false)
                .build();
        String userContextString = GatewayHmacUserContext.buildV2(
                signed.getUserId(),
                signed.getPlan(),
                signed.getRoles(),
                signed.isSubscriptionActive(),
                signed.getBillingModelType(),
                signed.getMeasurementType(),
                signed.getUnitLabel());
        String dataToSign = payload + Long.parseLong(timestamp) + userContextString;
        String signature = HmacUtils.calculateHmac(dataToSign, SECRET);

        Map<String, String> headers = new HashMap<>();
        headers.put(TollaraHeaders.SIGNATURE, signature);
        headers.put(TollaraHeaders.TIMESTAMP, timestamp);
        headers.put(TollaraHeaders.SIGNING_VERSION, "2");
        headers.put(TollaraHeaders.USER_ID, "user1");
        headers.put(TollaraHeaders.PLAN, "plan1");
        headers.put(TollaraHeaders.ROLES, "role1,role2");
        headers.put(TollaraHeaders.SUBSCRIPTION_ACTIVE, "false");

        TollaraRequestVerifier verifier = new TollaraRequestVerifier(SECRET);
        assertThat(verifier.verifyInboundHmac(headers::get, payload)).isTrue();
    }

    @Test
    void verifyInboundHmac_rejectsV1Canonical_whenGatewaySentV2Signature() throws Exception {
        String payload = "";
        String timestamp = "1700000000";
        SignedUserContext signed = SignedUserContext.builder()
                .userId("user1")
                .plan("plan1")
                .roles(List.of("role1", "role2"))
                .quotaRemaining(null)
                .subscriptionActive(false)
                .build();
        String userContextV2 = GatewayHmacUserContext.buildV2(
                signed.getUserId(),
                signed.getPlan(),
                signed.getRoles(),
                signed.isSubscriptionActive(),
                null,
                null,
                null);
        String dataToSign = payload + Long.parseLong(timestamp) + userContextV2;
        String signature = HmacUtils.calculateHmac(dataToSign, SECRET);

        Map<String, String> headers = new HashMap<>();
        headers.put(TollaraHeaders.SIGNATURE, signature);
        headers.put(TollaraHeaders.TIMESTAMP, timestamp);
        headers.put(TollaraHeaders.USER_ID, "user1");
        headers.put(TollaraHeaders.PLAN, "plan1");
        headers.put(TollaraHeaders.ROLES, "role1,role2");
        headers.put(TollaraHeaders.SUBSCRIPTION_ACTIVE, "false");

        TollaraRequestVerifier verifier = new TollaraRequestVerifier(SECRET);
        assertThat(verifier.verifyInboundHmac(headers::get, payload)).isFalse();
    }

    @Test
    void verifyInboundHmac_acceptsHmacSpecVectorExtended() throws Exception {
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

        TollaraRequestVerifier verifier = new TollaraRequestVerifier(SECRET);
        InboundHmacRequest req = InboundHmacRequest.builder()
                .signature(signature)
                .timestamp(timestamp)
                .payload(payload)
                .signedUserContext(signed)
                .build();
        assertThat(verifier.verifyInboundHmac(req)).isTrue();
    }

    @Test
    void verifyInboundHmac_withHeaderFunction_succeeds() throws Exception {
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
        headers.put(TollaraHeaders.SIGNATURE, signature);
        headers.put(TollaraHeaders.TIMESTAMP, timestamp);
        headers.put(TollaraHeaders.USER_ID, "user1");
        headers.put(TollaraHeaders.PLAN, "plan1");
        headers.put(TollaraHeaders.ROLES, "role1,role2");
        headers.put(TollaraHeaders.QUOTA_REMAINING, "10");
        headers.put(TollaraHeaders.SUBSCRIPTION_ACTIVE, "false");

        TollaraRequestVerifier verifier = new TollaraRequestVerifier(SECRET);
        assertThat(verifier.verifyInboundHmac(headers::get, payload)).isTrue();
    }

    @Test
    void verifyInboundHmac_withLowercaseHeaderMap_succeeds() throws Exception {
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
        headers.put("x-tollara-signature", signature);
        headers.put("x-tollara-timestamp", timestamp);
        headers.put("x-tollara-user-id", "user1");
        headers.put("x-tollara-plan", "plan1");
        headers.put("x-tollara-roles", "role1,role2");
        headers.put("x-tollara-quota-remaining", "10");
        headers.put("x-tollara-subscription-active", "false");

        TollaraRequestVerifier verifier = new TollaraRequestVerifier(SECRET);
        assertThat(verifier.verifyInboundHmac(headers, payload)).isTrue();
    }

    @Test
    void verify_matches_gateway_owner_like_context() throws Exception {
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

        TollaraRequestVerifier verifier = new TollaraRequestVerifier(SECRET_OWNER);
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
    void verify_matches_gateway_subscriber_with_billing_headers() throws Exception {
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

        TollaraRequestVerifier verifier = new TollaraRequestVerifier(SECRET_OWNER);
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
        Map<String, String> headers = new HashMap<>();
        headers.put(TollaraHeaders.USER_ID, "u1");
        headers.put(TollaraHeaders.SERVICE_PRODUCT_ID, "prod-1");
        headers.put(TollaraHeaders.SUBSCRIPTION_STATUS, "ACTIVE");
        headers.put(TollaraHeaders.BILLING_MODEL, "USAGE_POSTPAID");
        headers.put(TollaraHeaders.MEASUREMENT_TYPE, "PER_TOKEN");
        headers.put(TollaraHeaders.UNIT_LABEL, "token");

        TollaraRequestVerifier verifier = new TollaraRequestVerifier(SECRET);
        TollaraRequestVerifier.UserContext ctx = verifier.userContextFromHeaders(headers::get);
        assertThat(ctx.getUserId()).isEqualTo("u1");
        assertThat(ctx.getServiceProductId()).isEqualTo("prod-1");
        assertThat(ctx.getSubscriptionStatus()).isEqualTo("ACTIVE");
        assertThat(ctx.getBillingModelType()).isEqualTo("USAGE_POSTPAID");
        assertThat(ctx.getMeasurementType()).isEqualTo("PER_TOKEN");
        assertThat(ctx.getUnitLabel()).isEqualTo("token");
    }

    @Test
    void verifyInboundHmacAndGetUserContext_returnsContextWhenValid() throws Exception {
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
        headers.put(TollaraHeaders.SIGNATURE, signature);
        headers.put(TollaraHeaders.TIMESTAMP, timestamp);
        headers.put(TollaraHeaders.USER_ID, "user1");
        headers.put(TollaraHeaders.PLAN, "plan1");
        headers.put(TollaraHeaders.ROLES, "role1,role2");
        headers.put(TollaraHeaders.QUOTA_REMAINING, "10");
        headers.put(TollaraHeaders.SUBSCRIPTION_ACTIVE, "false");

        TollaraRequestVerifier verifier = new TollaraRequestVerifier(SECRET);
        Optional<TollaraRequestVerifier.UserContext> out =
                verifier.verifyInboundHmacAndGetUserContext(headers::get, payload);
        assertThat(out).isPresent();
        assertThat(out.get().getUserId()).isEqualTo("user1");
        assertThat(out.get().getPlan()).isEqualTo("plan1");
    }

    @Test
    void verifyInboundHmacAndGetUserContext_emptyWhenSignatureInvalid() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put(TollaraHeaders.SIGNATURE, "bad");
        headers.put(TollaraHeaders.TIMESTAMP, "1700000000");
        headers.put(TollaraHeaders.USER_ID, "user1");

        TollaraRequestVerifier verifier = new TollaraRequestVerifier(SECRET);
        Optional<TollaraRequestVerifier.UserContext> out =
                verifier.verifyInboundHmacAndGetUserContext(headers, "");
        assertThat(out).isEmpty();
    }
}
