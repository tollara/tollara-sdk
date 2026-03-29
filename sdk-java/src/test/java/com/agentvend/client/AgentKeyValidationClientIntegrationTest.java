package com.agentvend.client;

import com.agentvend.common.util.HmacUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import java.math.BigDecimal;
import java.net.http.HttpClient;

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
            {"valid":true,"userId":"user-123","agentId":"%s","plan":"basic","roles":["user"],"quotaRemaining":100,"subscriptionActive":true,"billingModelType":null,"measurementType":null,"unitLabel":null,"timestamp":1700000000,"error":null}
            """.formatted(AGENT_ID).trim();
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
}
