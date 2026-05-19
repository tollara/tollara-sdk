package com.tollara.client;

import com.tollara.client.model.UsageEstimateResult;
import com.tollara.common.util.HmacUtils;
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
 * Integration tests for ServiceKeyValidationClient against WireMock stubs of the Tollara Core API
 * (see docs-sdk/MAIN-SDK-API-SPEC.md §2).
 */
class ServiceKeyValidationClientIntegrationTest {

    private static final String SERVICE_SECRET = "test-service-secret";
    private static final String SERVICE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SERVICE_KEY_ID = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

    @RegisterExtension
    static WireMockExtension wireMock = newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private ServiceKeyValidationClient client;
    private String coreBaseUrl;

    @BeforeEach
    void setUp() {
        int port = wireMock.getPort();
        coreBaseUrl = "http://localhost:" + port + "/api/v1";
        client = new ServiceKeyValidationClient(coreBaseUrl, SERVICE_ID, SERVICE_SECRET, HttpClient.newHttpClient());
    }

    @Test
    void validateServiceKey_returnsResult_whenCoreReturns200WithValidHmac() throws Exception {
        String responseBody = """
            {"valid":true,"serviceKeyId":"%s","userId":"user-123","serviceId":"%s","plan":"basic","roles":["user"],"subscriptionActive":true,"billingModelType":null,"measurementType":null,"unitLabel":null,"timestamp":1700000000,"error":null,"validationSchemaVersion":2}
            """.formatted(SERVICE_KEY_ID, SERVICE_ID).trim();
        String timestamp = "1700000000";
        String canonical = responseBody + timestamp;
        String signature = HmacUtils.calculateHmac(canonical, SERVICE_SECRET);

        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/service-keys/validate"))
                        .withRequestBody(containing("serviceKey"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("X-Tollara-Signature", signature)
                                .withHeader("X-Tollara-Timestamp", timestamp)
                                .withBody(responseBody))
        );

        ServiceKeyValidationClient.ServiceKeyValidationResult result = client.validateServiceKey("bearer-token-xyz");

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getServiceId()).isEqualTo(SERVICE_ID);
        assertThat(result.getPlan()).isEqualTo("basic");
        assertThat(result.getRoles()).containsExactly("user");
        assertThat(result.getQuotaRemaining()).isNull();
        assertThat(result.isSubscriptionActive()).isTrue();
        assertThat(result.getServiceKeyId()).isEqualTo(UUID.fromString(SERVICE_KEY_ID));
    }

    @Test
    void validateServiceKey_returnsNull_whenCoreReturns401() {
        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/service-keys/validate"))
                        .willReturn(aResponse()
                                .withStatus(401)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"valid\":false,\"error\":\"Invalid key\"}"))
        );

        ServiceKeyValidationClient.ServiceKeyValidationResult result = client.validateServiceKey("invalid-key");

        assertThat(result).isNull();
    }

    @Test
    void validateServiceKey_returnsNull_whenHmacSignatureInvalid() throws Exception {
        String responseBody = "{\"valid\":true,\"userId\":\"user-123\",\"serviceId\":\"" + SERVICE_ID + "\",\"plan\":\"basic\",\"roles\":[],\"subscriptionActive\":true,\"timestamp\":1700000000,\"validationSchemaVersion\":2}";
        String timestamp = "1700000000";
        String badSignature = HmacUtils.calculateHmac(responseBody + timestamp, "wrong-secret");

        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/service-keys/validate"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("X-Tollara-Signature", badSignature)
                                .withHeader("X-Tollara-Timestamp", timestamp)
                                .withBody(responseBody))
        );

        ServiceKeyValidationClient.ServiceKeyValidationResult result = client.validateServiceKey("bearer-token");

        assertThat(result).isNull();
    }

    @Test
    void validateServiceKey_returnsNull_whenValidFalseInBody() throws Exception {
        String responseBody = "{\"valid\":false,\"userId\":null,\"serviceId\":null,\"plan\":null,\"roles\":[],\"subscriptionActive\":false,\"billingModelType\":null,\"measurementType\":null,\"unitLabel\":null,\"timestamp\":1700000000,\"error\":\"Key expired\"}";
        String timestamp = "1700000000";
        String signature = HmacUtils.calculateHmac(responseBody + timestamp, SERVICE_SECRET);

        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/service-keys/validate"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("X-Tollara-Signature", signature)
                                .withHeader("X-Tollara-Timestamp", timestamp)
                                .withBody(responseBody))
        );

        ServiceKeyValidationClient.ServiceKeyValidationResult result = client.validateServiceKey("expired-key");

        assertThat(result).isNull();
    }

    @Test
    void estimateUsage_returnsResult_whenCoreReturns200WithValidHmac() throws Exception {
        String responseBody = """
            {"sufficientCredits":true,"wouldExceedCap":false,"wouldAllow":true,"estimatedCost":0.1,"remainingCredits":null,"remainingSpendingCap":null,"billingModelType":"SUBSCRIPTION","measurementType":"PER_REQUEST","unitLabel":"request","breakdown":null,"estimateSchemaVersion":1,"timestamp":1700000000}
            """.trim();
        String timestamp = "1700000000";
        String canonical = responseBody + timestamp;
        String signature = HmacUtils.calculateHmac(canonical, SERVICE_SECRET);

        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/service-keys/estimate-usage"))
                        .withRequestBody(matchingJsonPath("$.serviceKey"))
                        .withRequestBody(matchingJsonPath("$.estimatedUnits"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("X-Tollara-Signature", signature)
                                .withHeader("X-Tollara-Timestamp", timestamp)
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
                post(urlPathEqualTo("/api/v1/service-keys/estimate-usage"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("X-Tollara-Signature", badSig)
                                .withHeader("X-Tollara-Timestamp", timestamp)
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
