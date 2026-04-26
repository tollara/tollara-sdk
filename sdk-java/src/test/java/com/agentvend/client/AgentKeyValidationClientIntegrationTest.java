package com.agentvend.client;

import com.agentvend.client.model.UsageEstimateResult;
import com.agentvend.common.util.HmacUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.junit5.WireMockExtension.newInstance;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Integration tests for AgentKeyValidationClient against WireMock stubs of the AgentVend Core API
 * (see docs/sdk-api-spec.md §2).
 */
class AgentKeyValidationClientIntegrationTest {

    private static final String AGENT_SECRET = "test-agent-secret";
    private static final String AGENT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String AGENT_KEY_ID = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

    @RegisterExtension
    static WireMockExtension wireMock = newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private AgentKeyValidationClient client;
    private String coreBaseUrl;

    @BeforeEach
    void setUp() {
        int port = wireMock.getPort();
        coreBaseUrl = "http://localhost:" + port + "/api/v1";
        client = new AgentKeyValidationClient(coreBaseUrl, AGENT_ID, AGENT_SECRET, HttpClient.newHttpClient());
    }

    @Test
    void validateAgentKey_returnsResult_whenCoreReturns200WithValidHmac() throws Exception {
        // Per API spec §2.1: response has X-AgentVend-Signature = HMAC(responseBody + timestamp, agentSecret)
        // Must match ObjectMapper.writeValueAsString(ValidationResponse) after deserialize (includes null optional fields).
        String responseBody = """
            {"valid":true,"agentKeyId":"%s","userId":"user-123","agentId":"%s","plan":"basic","roles":["user"],"quotaRemaining":100,"subscriptionActive":true,"billingModelType":null,"measurementType":null,"unitLabel":null,"timestamp":1700000000,"error":null,"validationSchemaVersion":1}
            """.formatted(AGENT_KEY_ID, AGENT_ID).trim();
        String timestamp = "1700000000";
        String canonical = responseBody + timestamp;
        String signature = HmacUtils.calculateHmac(canonical, AGENT_SECRET);

        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/agent-keys/validate"))
                        .withRequestBody(containing("agentKey"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("X-AgentVend-Signature", signature)
                                .withHeader("X-AgentVend-Timestamp", timestamp)
                                .withBody(responseBody))
        );

        AgentKeyValidationClient.AgentKeyValidationResult result = client.validateAgentKey("bearer-token-xyz");

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getAgentId()).isEqualTo(AGENT_ID);
        assertThat(result.getPlan()).isEqualTo("basic");
        assertThat(result.getRoles()).containsExactly("user");
        assertThat(result.getQuotaRemaining()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(result.isSubscriptionActive()).isTrue();
        assertThat(result.getAgentKeyId()).isEqualTo(UUID.fromString(AGENT_KEY_ID));
    }

    @Test
    void validateAgentKey_returnsNull_whenCoreReturns401() {
        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/agent-keys/validate"))
                        .willReturn(aResponse()
                                .withStatus(401)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"valid\":false,\"error\":\"Invalid key\"}"))
        );

        AgentKeyValidationClient.AgentKeyValidationResult result = client.validateAgentKey("invalid-key");

        assertThat(result).isNull();
    }

    @Test
    void validateAgentKey_returnsNull_whenHmacSignatureInvalid() throws Exception {
        String responseBody = "{\"valid\":true,\"userId\":\"user-123\",\"agentId\":\"" + AGENT_ID + "\",\"plan\":\"basic\",\"roles\":[],\"quotaRemaining\":100,\"subscriptionActive\":true,\"timestamp\":1700000000}";
        String timestamp = "1700000000";
        // Wrong signature (wrong secret used)
        String badSignature = HmacUtils.calculateHmac(responseBody + timestamp, "wrong-secret");

        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/agent-keys/validate"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("X-AgentVend-Signature", badSignature)
                                .withHeader("X-AgentVend-Timestamp", timestamp)
                                .withBody(responseBody))
        );

        AgentKeyValidationClient.AgentKeyValidationResult result = client.validateAgentKey("bearer-token");

        assertThat(result).isNull();
    }

    @Test
    void validateAgentKey_returnsNull_whenValidFalseInBody() throws Exception {
        String responseBody = "{\"valid\":false,\"userId\":null,\"agentId\":null,\"plan\":null,\"roles\":[],\"quotaRemaining\":null,\"subscriptionActive\":false,\"billingModelType\":null,\"measurementType\":null,\"unitLabel\":null,\"timestamp\":1700000000,\"error\":\"Key expired\"}";
        String timestamp = "1700000000";
        String signature = HmacUtils.calculateHmac(responseBody + timestamp, AGENT_SECRET);

        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/agent-keys/validate"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("X-AgentVend-Signature", signature)
                                .withHeader("X-AgentVend-Timestamp", timestamp)
                                .withBody(responseBody))
        );

        AgentKeyValidationClient.AgentKeyValidationResult result = client.validateAgentKey("expired-key");

        assertThat(result).isNull();
    }

    @Test
    void estimateUsage_returnsResult_whenCoreReturns200WithValidHmac() throws Exception {
        String responseBody = """
            {"sufficientCredits":true,"wouldExceedCap":false,"wouldAllow":true,"estimatedCost":0.1,"remainingCredits":null,"remainingSpendingCap":null,"billingModelType":"SUBSCRIPTION","measurementType":"PER_REQUEST","unitLabel":"request","breakdown":null,"estimateSchemaVersion":1,"timestamp":1700000000}
            """.trim();
        String timestamp = "1700000000";
        String canonical = responseBody + timestamp;
        String signature = HmacUtils.calculateHmac(canonical, AGENT_SECRET);

        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/agent-keys/estimate-usage"))
                        .withRequestBody(matchingJsonPath("$.agentKey"))
                        .withRequestBody(matchingJsonPath("$.estimatedUnits"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("X-AgentVend-Signature", signature)
                                .withHeader("X-AgentVend-Timestamp", timestamp)
                                .withBody(responseBody))
        );

        UsageEstimateResult result = client.estimateUsage("key-1", new BigDecimal("1.5"));

        assertThat(result).isNotNull();
        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.isWouldAllow()).isTrue();
        assertThat(result.isSufficientCredits()).isTrue();
        assertThat(result.getEstimateSchemaVersion()).isEqualTo(1);
        assertThat(result.getTimestamp()).isEqualTo(1700000000L);
        assertThat(result.getBillingModelType()).isEqualTo("SUBSCRIPTION");
    }

    @Test
    void estimateUsage_returnsNull_whenHmacInvalid() throws Exception {
        String responseBody = """
            {"sufficientCredits":false,"wouldExceedCap":true,"wouldAllow":false,"estimatedCost":null,"remainingCredits":null,"remainingSpendingCap":null,"billingModelType":"PREPAID","measurementType":null,"unitLabel":null,"breakdown":null,"estimateSchemaVersion":1,"timestamp":1700000000}
            """.trim();
        String timestamp = "1700000000";
        String badSig = HmacUtils.calculateHmac(responseBody + timestamp, "wrong-secret");

        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/agent-keys/estimate-usage"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("X-AgentVend-Signature", badSig)
                                .withHeader("X-AgentVend-Timestamp", timestamp)
                                .withBody(responseBody))
        );

        assertThat(client.estimateUsage("k", BigDecimal.ONE)).isNull();
    }

    @Test
    void estimateUsage_returnsNull_whenEstimatedUnitsNotPositive() {
        assertThat(client.estimateUsage("k", BigDecimal.ZERO)).isNull();
        assertThat(client.estimateUsage("k", new BigDecimal("-1"))).isNull();
    }
}
