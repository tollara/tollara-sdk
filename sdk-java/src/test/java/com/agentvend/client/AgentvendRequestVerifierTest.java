package com.agentvend.client;

import com.agentvend.client.model.InboundHmacRequest;
import com.agentvend.client.model.SignedUserContext;
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

    /** Vector aligned with docs/hmac-spec.md inbound example. */
    @Test
    void verifyInboundHmac_acceptsHmacSpecVector() throws Exception {
        String payload = "";
        String timestamp = "1700000000";
        SignedUserContext signed = SignedUserContext.builder()
                .userId("user1")
                .plan("plan1")
                .roles(List.of("role1", "role2"))
                .quotaRemaining(new BigDecimal("10"))
                .build();
        String userContextString = "user1plan1role1,role210";
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
    void verifyInboundHmac_withHttpHeaders_succeeds() throws Exception {
        String payload = "";
        String timestamp = "1700000000";
        String userContextString = "user1plan1role1,role210";
        String dataToSign = payload + Long.parseLong(timestamp) + userContextString;
        String signature = HmacUtils.calculateHmac(dataToSign, SECRET);

        HttpHeaders headers = new HttpHeaders();
        headers.add(AgentVendHeaders.SIGNATURE, signature);
        headers.add(AgentVendHeaders.TIMESTAMP, timestamp);
        headers.add(AgentVendHeaders.USER_ID, "user1");
        headers.add(AgentVendHeaders.PLAN, "plan1");
        headers.add(AgentVendHeaders.ROLES, "role1,role2");
        headers.add(AgentVendHeaders.QUOTA_REMAINING, "10");

        AgentvendRequestVerifier verifier = new AgentvendRequestVerifier(SECRET);
        assertThat(verifier.verifyInboundHmac(headers, payload)).isTrue();
    }

    @Test
    void verifyInboundHmac_withLowercaseHeaderMap_succeeds() throws Exception {
        String payload = "";
        String timestamp = "1700000000";
        String userContextString = "user1plan1role1,role210";
        String dataToSign = payload + Long.parseLong(timestamp) + userContextString;
        String signature = HmacUtils.calculateHmac(dataToSign, SECRET);

        Map<String, String> headers = new HashMap<>();
        headers.put("x-agentvend-signature", signature);
        headers.put("x-agentvend-timestamp", timestamp);
        headers.put("x-agentvend-user-id", "user1");
        headers.put("x-agentvend-plan", "plan1");
        headers.put("x-agentvend-roles", "role1,role2");
        headers.put("x-agentvend-quota-remaining", "10");

        AgentvendRequestVerifier verifier = new AgentvendRequestVerifier(SECRET);
        assertThat(verifier.verifyInboundHmac(headers, payload)).isTrue();
    }

    @Test
    void userContextFromHeaders_parsesSubscriptionActive() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(AgentVendHeaders.USER_ID, "u1");
        headers.add(AgentVendHeaders.SUBSCRIPTION_ACTIVE, "true");

        AgentvendRequestVerifier verifier = new AgentvendRequestVerifier(SECRET);
        AgentvendRequestVerifier.UserContext ctx = verifier.userContextFromHeaders(headers);
        assertThat(ctx.getUserId()).isEqualTo("u1");
        assertThat(ctx.isSubscriptionActive()).isTrue();
    }

    @Test
    void deprecatedVerifyHmacSignature_delegatesConsistently() throws Exception {
        String payload = "";
        String timestamp = "1700000000";
        String userContextString = "user1plan1role1,role210";
        String dataToSign = payload + Long.parseLong(timestamp) + userContextString;
        String signature = HmacUtils.calculateHmac(dataToSign, SECRET);

        AgentvendRequestVerifier verifier = new AgentvendRequestVerifier(SECRET);
        assertThat(verifier.verifyHmacSignature(
                        signature,
                        timestamp,
                        payload,
                        "user1",
                        "plan1",
                        List.of("role1", "role2"),
                        new BigDecimal("10")))
                .isTrue();
    }
}
