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
    private static final String SERVICE_PRODUCT_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

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
    void validateServiceKey_returnsResult_whenCoreReturns200WithValidHmacV3() throws Exception {
        String responseBody = """
            {"valid":true,"serviceKeyId":"%s","userId":"user-123","serviceId":"%s","serviceProductId":"%s","roles":["user"],"subscriptionStatus":"ACTIVE","billingModelType":"SUBSCRIPTION","measurementType":"PER_REQUEST","unitLabel":"request","timestamp":1700000000,"error":null,"validationSchemaVersion":3}
            """.formatted(SERVICE_KEY_ID, SERVICE_ID, SERVICE_PRODUCT_ID).trim();
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
        assertThat(result.getServiceProductId()).isEqualTo(SERVICE_PRODUCT_ID);
        assertThat(result.getSubscriptionStatus()).isEqualTo("ACTIVE");
        assertThat(result.getValidationSchemaVersion()).isEqualTo(3);
        assertThat(result.getRoles()).containsExactly("user");
        assertThat(result.grantAccess()).isTrue();
        assertThat(result.getServiceKeyId()).isEqualTo(UUID.fromString(SERVICE_KEY_ID));
        assertThat(result.getBillingModelType()).isEqualTo("SUBSCRIPTION");
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
        String responseBody = "{\"valid\":true,\"userId\":\"user-123\",\"serviceId\":\"" + SERVICE_ID
                + "\",\"serviceProductId\":\"" + SERVICE_PRODUCT_ID
                + "\",\"roles\":[],\"subscriptionStatus\":\"ACTIVE\",\"timestamp\":1700000000,\"validationSchemaVersion\":3}";
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
        String responseBody = "{\"valid\":false,\"userId\":null,\"serviceId\":null,\"serviceProductId\":null,\"roles\":[],\"subscriptionStatus\":null,\"billingModelType\":null,\"measurementType\":null,\"unitLabel\":null,\"timestamp\":1700000000,\"error\":\"Key expired\"}";
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
    void validateServiceKey_grantAccessFalseForExpiredStatus() throws Exception {
        String responseBody = """
            {"valid":true,"serviceKeyId":"%s","userId":"user-123","serviceId":"%s","serviceProductId":"%s","roles":[],"subscriptionStatus":"EXPIRED","billingModelType":null,"measurementType":null,"unitLabel":null,"timestamp":1700000000,"error":null,"validationSchemaVersion":3}
            """.formatted(SERVICE_KEY_ID, SERVICE_ID, SERVICE_PRODUCT_ID).trim();
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

        assertThat(result).isNotNull();
        assertThat(result.grantAccess()).isFalse();
        assertThat(ServiceKeyValidationClient.ServiceKeyValidationResult.grantAccess("EXPIRED")).isFalse();
    }

    @Test
    void validateServiceKeyWithOutcome_returnsInvalidKeyWithMessage() throws Exception {
        String responseBody = """
            {"valid":false,"error":"Key expired"}
            """.trim();
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

        ServiceKeyValidationClient.ServiceKeyValidationOutcome outcome =
                client.validateServiceKeyWithOutcome("expired-key");

        assertThat(outcome.isOk()).isFalse();
        assertThat(outcome.getFailure().getCode())
                .isEqualTo(ServiceKeyValidationClient.ValidationFailureCode.INVALID_KEY);
        assertThat(outcome.getFailure().getMessage()).isEqualTo("Key expired");
    }

    @Test
    void validateServiceKeyWithOutcome_returnsMissingKey_withoutHttpCall() {
        ServiceKeyValidationClient.ServiceKeyValidationOutcome outcome =
                client.validateServiceKeyWithOutcome("   ");

        assertThat(outcome.isOk()).isFalse();
        assertThat(outcome.getFailure().getCode())
                .isEqualTo(ServiceKeyValidationClient.ValidationFailureCode.MISSING_KEY);
    }

    @Test
    void validateServiceKeyWithOutcome_returnsHttpError_on401WithoutJsonBody() {
        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/service-keys/validate"))
                        .willReturn(aResponse()
                                .withStatus(401)
                                .withBody("unauthorized"))
        );

        ServiceKeyValidationClient.ServiceKeyValidationOutcome outcome =
                client.validateServiceKeyWithOutcome("bad-key");

        assertThat(outcome.isOk()).isFalse();
        assertThat(outcome.getFailure().getCode())
                .isEqualTo(ServiceKeyValidationClient.ValidationFailureCode.HTTP_ERROR);
        assertThat(outcome.getFailure().getHttpStatus()).isEqualTo(401);
    }

    @Test
    void validateServiceKeyWithOutcome_returnsInvalidKey_onUnsigned401WithValidFalse() {
        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/service-keys/validate"))
                        .willReturn(aResponse()
                                .withStatus(401)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"valid\":false,\"error\":\"Invalid service key\"}"))
        );

        ServiceKeyValidationClient.ServiceKeyValidationOutcome outcome =
                client.validateServiceKeyWithOutcome("bad-key");

        assertThat(outcome.isOk()).isFalse();
        assertThat(outcome.getFailure().getCode())
                .isEqualTo(ServiceKeyValidationClient.ValidationFailureCode.INVALID_KEY);
        assertThat(outcome.getFailure().getMessage()).isEqualTo("Invalid service key");
        assertThat(outcome.getFailure().getHttpStatus()).isEqualTo(401);
    }

    @Test
    void validateServiceKeyWithOutcome_returnsHttpError_on500WithValidFalse() {
        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/service-keys/validate"))
                        .willReturn(aResponse()
                                .withStatus(500)
                                .withBody("{\"valid\":false,\"error\":\"Internal server error\"}"))
        );

        ServiceKeyValidationClient.ServiceKeyValidationOutcome outcome =
                client.validateServiceKeyWithOutcome("bad-key");

        assertThat(outcome.isOk()).isFalse();
        assertThat(outcome.getFailure().getCode())
                .isEqualTo(ServiceKeyValidationClient.ValidationFailureCode.HTTP_ERROR);
        assertThat(outcome.getFailure().getHttpStatus()).isEqualTo(500);
    }

    @Test
    void validateServiceKeyWithOutcome_returnsHmacMismatch() {
        String responseBody = "{\"valid\":true,\"userId\":\"u1\"}";
        wireMock.stubFor(
                post(urlPathEqualTo("/api/v1/service-keys/validate"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("X-Tollara-Signature", "bad-signature")
                                .withHeader("X-Tollara-Timestamp", "1700000000")
                                .withBody(responseBody))
        );

        ServiceKeyValidationClient.ServiceKeyValidationOutcome outcome =
                client.validateServiceKeyWithOutcome("k");

        assertThat(outcome.isOk()).isFalse();
        assertThat(outcome.getFailure().getCode())
                .isEqualTo(ServiceKeyValidationClient.ValidationFailureCode.HMAC_MISMATCH);
        assertThat(outcome.getFailure().getHttpStatus()).isEqualTo(200);
    }

    @Test
    void validateServiceKeyWithOutcome_returnsNetwork_onConnectionFailure() {
        ServiceKeyValidationClient deadClient = new ServiceKeyValidationClient(
                "http://127.0.0.1:1/api/v1", SERVICE_ID, SERVICE_SECRET, HttpClient.newHttpClient());

        ServiceKeyValidationClient.ServiceKeyValidationOutcome outcome =
                deadClient.validateServiceKeyWithOutcome("k");

        assertThat(outcome.isOk()).isFalse();
        assertThat(outcome.getFailure().getCode())
                .isEqualTo(ServiceKeyValidationClient.ValidationFailureCode.NETWORK);
    }

    @Test
    void estimateUsage_returnsResult_whenCoreReturns200WithValidHmac() throws Exception {
        String responseBody = """
            {"sufficientCredits":true,"wouldExceedCap":false,"wouldAllow":true,"estimatedCost":0.1,"billingModelType":"SUBSCRIPTION","measurementType":"PER_REQUEST","unitLabel":"request","breakdown":{"unitsRemaining":199,"remainingSpendingCap":20,"isOverLimit":false},"estimateSchemaVersion":3,"timestamp":1700000000}
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
        assertThat(result.getEstimateSchemaVersion()).isEqualTo(3);
        assertThat(result.getTimestamp()).isEqualTo(1700000000L);
        assertThat(result.getBillingModelType()).isEqualTo("SUBSCRIPTION");
        assertThat(result.getBreakdown()).isNotNull();
        assertThat(result.getBreakdown().getUnitsRemaining()).isEqualByComparingTo(new BigDecimal("199"));
        assertThat(result.getBreakdown().getRemainingSpendingCap()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(result.getBreakdown().getOverLimit()).isFalse();
    }

    @Test
    void estimateUsage_returnsNull_whenHmacInvalid() throws Exception {
        String responseBody = """
            {"sufficientCredits":false,"wouldExceedCap":true,"wouldAllow":false,"estimatedCost":null,"billingModelType":"PREPAID","measurementType":null,"unitLabel":null,"breakdown":null,"estimateSchemaVersion":3,"timestamp":1700000000}
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
